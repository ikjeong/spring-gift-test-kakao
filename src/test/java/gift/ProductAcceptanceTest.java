package gift;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAcceptanceTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Sql(scripts = "classpath:cleanup.sql")
    @Sql(scripts = "classpath:product-test-data.sql")
    @Test
    void 상품을_생성하면_목록_조회_시_조회된다() {
        // given
        String productName = "아이스 아메리카노";
        int productPrice = 4500;
        String imageUrl = "https://example.com/image.png";
        Long categoryId = 1L;
        String body = """
                {
                    "name": "%s",
                    "price": %d,
                    "imageUrl": "%s",
                    "categoryId": %d
                }
                """.formatted(productName, productPrice, imageUrl, categoryId);

        // when — 상품 생성
        ExtractableResponse<Response> createResponse = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/products")
                .then().log().all()
                .statusCode(HttpStatus.OK.value())
                .extract();

        Long createdId = createResponse.jsonPath().getLong("id");

        // then — 목록 조회로 생성 확인
        ExtractableResponse<Response> listResponse = RestAssured.given().log().all()
                .when()
                .get("/api/products")
                .then().log().all()
                .statusCode(HttpStatus.OK.value())
                .extract();

        List<Long> ids = listResponse.jsonPath().getList("id", Long.class);
        assertThat(ids).containsExactly(createdId);
    }

    @Sql(scripts = "classpath:cleanup.sql")
    @Test
    void 존재하지_않는_카테고리로_상품을_생성하면_실패한다() {
        // given
        Long nonExistentCategoryId = 999L;
        String body = """
                {
                    "name": "아이스 아메리카노",
                    "price": 4500,
                    "imageUrl": "https://example.com/image.png",
                    "categoryId": %d
                }
                """.formatted(nonExistentCategoryId);

        // when & then
        RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/products")
                .then().log().all()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
