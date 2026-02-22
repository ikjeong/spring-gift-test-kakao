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
class CategoryAcceptanceTest {

    @LocalServerPort
    int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    // NOTE: CreateCategoryRequest에 setter가 없고 @RequestBody 어노테이션이 누락되어
    // @ModelAttribute 바인딩 시 name 필드가 null로 저장됩니다.
    @Sql(scripts = "classpath:cleanup.sql")
    @Test
    void 카테고리를_생성하면_목록_조회_시_조회된다() {
        // given
        String categoryName = "교환권";

        // when — 카테고리 생성
        ExtractableResponse<Response> createResponse = RestAssured.given().log().all()
                .contentType(ContentType.URLENC)
                .formParam("name", categoryName)
                .when()
                .post("/api/categories")
                .then().log().all()
                .statusCode(HttpStatus.OK.value())
                .extract();

        Long createdId = createResponse.jsonPath().getLong("id");

        // then — 목록 조회로 생성 확인
        ExtractableResponse<Response> response = RestAssured.given().log().all()
                .when()
                .get("/api/categories")
                .then().log().all()
                .statusCode(HttpStatus.OK.value())
                .extract();

        List<Long> ids = response.jsonPath().getList("id", Long.class);
        assertThat(ids).containsExactly(createdId);
    }
}