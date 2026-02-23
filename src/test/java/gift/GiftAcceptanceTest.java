package gift;

import gift.fixture.CategoryFixture;
import gift.fixture.MemberFixture;
import gift.fixture.OptionFixture;
import gift.fixture.ProductFixture;
import gift.model.Option;
import gift.support.DatabaseCleaner;
import gift.support.TestDataInitializer;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiftAcceptanceTest {

    @LocalServerPort
    int port;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Autowired
    TestDataInitializer initializer;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Long senderId;
    private Long receiverId;
    private Long optionId;
    private Option option;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        databaseCleaner.clear();

        senderId = initializer.saveMember(MemberFixture.발신회원());
        receiverId = initializer.saveMember(MemberFixture.수신회원());
        Long categoryId = initializer.saveCategory(CategoryFixture.기본카테고리());
        Long productId = initializer.saveProduct(ProductFixture.기본상품(), categoryId);
        option = OptionFixture.재고10개옵션();
        optionId = initializer.saveOption(option, productId);
    }

    @Test
    void 선물하기가_정상적으로_처리되면_옵션_재고가_차감된다() {
        // given
        int giftQuantity = 3;
        String body = """
                {
                    "optionId": %d,
                    "quantity": %d,
                    "receiverId": %d,
                    "message": "생일 축하해!"
                }
                """.formatted(optionId, giftQuantity, receiverId);

        // when
        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", senderId)
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .statusCode(HttpStatus.OK.value());

        // then — 조회 API 미제공으로 DB에서 재고 차감 직접 확인
        Integer remainingQuantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM option WHERE id = ?", Integer.class, optionId);
        assertThat(remainingQuantity).isEqualTo(option.getQuantity() - giftQuantity);
    }

    @Test
    void 재고보다_많은_수량을_선물하면_실패하고_재고는_변경되지_않는다() {
        // given
        int overQuantity = option.getQuantity() + 1;
        String body = """
                {
                    "optionId": %d,
                    "quantity": %d,
                    "receiverId": %d,
                    "message": "선물"
                }
                """.formatted(optionId, overQuantity, receiverId);

        // when
        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", senderId)
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());

        // then — 트랜잭션 롤백으로 재고가 변경되지 않았는지 확인
        Integer remainingQuantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM option WHERE id = ?", Integer.class, optionId);
        assertThat(remainingQuantity).isEqualTo(option.getQuantity());
    }

    @Test
    void 존재하지_않는_옵션으로_선물하면_실패한다() {
        // given
        Long nonExistentOptionId = 999L;
        String body = """
                {
                    "optionId": %d,
                    "quantity": 1,
                    "receiverId": %d,
                    "message": "선물"
                }
                """.formatted(nonExistentOptionId, receiverId);

        // when & then
        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", senderId)
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    void 존재하지_않는_발신자로_선물하면_실패한다() {
        // given
        Long nonExistentMemberId = 999L;
        String body = """
                {
                    "optionId": %d,
                    "quantity": 1,
                    "receiverId": %d,
                    "message": "선물"
                }
                """.formatted(optionId, receiverId);

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
