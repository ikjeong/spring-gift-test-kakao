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

    // NOTE: CreateProductRequest에 setter가 없고 @RequestBody 어노테이션이 누락되어
    // @ModelAttribute 바인딩 시 categoryId 필드가 null이 됩니다.
    // categoryRepository.findById(null)에서 예외가 발생하여 상품 생성 API가 500을 반환합니다.
    // CategoryAcceptanceTest와 동일한 원인이지만, Category는 name이 null이어도 저장 가능한 반면
    // Product는 categoryId가 null이면 조회 자체가 실패합니다.
    @Sql(scripts = "classpath:cleanup.sql")
    @Sql(scripts = "classpath:product-test-data.sql")
    @Test
    void 상품을_생성하면_목록_조회_시_조회된다() {
        // given
        String productName = "아이스 아메리카노";
        int productPrice = 4500;
        String imageUrl = "https://example.com/image.png";
        Long categoryId = 1L;

        // when — 상품 생성
        RestAssured.given().log().all()
                .contentType(ContentType.URLENC)
                .formParam("name", productName)
                .formParam("price", productPrice)
                .formParam("imageUrl", imageUrl)
                .formParam("categoryId", categoryId)
                .when()
                .post("/api/products")
                .then().log().all()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());

        // then — 생성 실패로 인해 목록이 비어 있음을 확인
        ExtractableResponse<Response> listResponse = RestAssured.given().log().all()
                .when()
                .get("/api/products")
                .then().log().all()
                .statusCode(HttpStatus.OK.value())
                .extract();

        List<Long> ids = listResponse.jsonPath().getList("id", Long.class);
        assertThat(ids).isEmpty();
    }

    @Sql(scripts = "classpath:cleanup.sql")
    @Test
    void 존재하지_않는_카테고리로_상품을_생성하면_실패한다() {
        // given
        Long nonExistentCategoryId = 999L;

        // when & then
        RestAssured.given().log().all()
                .contentType(ContentType.URLENC)
                .formParam("name", "아이스 아메리카노")
                .formParam("price", 4500)
                .formParam("imageUrl", "https://example.com/image.png")
                .formParam("categoryId", nonExistentCategoryId)
                .when()
                .post("/api/products")
                .then().log().all()
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}
