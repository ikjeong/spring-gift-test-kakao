package gift;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiftAcceptanceTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Sql(scripts = "classpath:cleanup.sql")
    @Sql(scripts = "classpath:gift-test-data.sql")
    @Test
    void 선물하기가_정상적으로_처리되면_옵션_재고가_차감된다() {
        // given
        String body = """
                {
                    "optionId": 1,
                    "quantity": 3,
                    "receiverId": 2,
                    "message": "생일 축하해!"
                }
                """;

        // when
        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", 1L)
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .statusCode(HttpStatus.OK.value());

        // then — 조회 API 미제공으로 DB에서 재고 차감 직접 확인
        Integer remainingQuantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM option WHERE id = 1", Integer.class);
        assertThat(remainingQuantity).isEqualTo(7);
    }

    @Sql(scripts = "classpath:cleanup.sql")
    @Sql(scripts = "classpath:gift-test-data.sql")
    @Test
    void 재고보다_많은_수량을_선물하면_실패하고_재고는_변경되지_않는다() {
        // given
        String body = """
                {
                    "optionId": 1,
                    "quantity": 100,
                    "receiverId": 2,
                    "message": "선물"
                }
                """;

        // when
        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", 1L)
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());

        // then — 트랜잭션 롤백으로 재고가 변경되지 않았는지 확인
        Integer remainingQuantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM option WHERE id = 1", Integer.class);
        assertThat(remainingQuantity).isEqualTo(10);
    }

    @Sql(scripts = "classpath:cleanup.sql")
    @Sql(scripts = "classpath:gift-test-data.sql")
    @Test
    void 존재하지_않는_옵션으로_선물하면_실패한다() {
        // given
        Long nonExistentOptionId = 999L;
        String body = """
                {
                    "optionId": %d,
                    "quantity": 1,
                    "receiverId": 2,
                    "message": "선물"
                }
                """.formatted(nonExistentOptionId);

        // when & then
        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", 1L)
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Sql(scripts = "classpath:cleanup.sql")
    @Sql(scripts = "classpath:gift-test-data.sql")
    @Test
    void 존재하지_않는_발신자로_선물하면_실패한다() {
        // given
        Long nonExistentMemberId = 999L;
        String body = """
                {
                    "optionId": 1,
                    "quantity": 1,
                    "receiverId": 2,
                    "message": "선물"
                }
                """;

        // when & then
        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", nonExistentMemberId)
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
