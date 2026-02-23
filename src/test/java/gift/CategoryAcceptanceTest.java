package gift;

import gift.support.DatabaseCleaner;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryAcceptanceTest {

    @LocalServerPort
    int port;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        databaseCleaner.clear();
    }

    @Test
    void 카테고리를_생성하면_목록_조회_시_조회된다() {
        // given
        String categoryName = "교환권";
        String body = """
                {
                    "name": "%s"
                }
                """.formatted(categoryName);

        // when — 카테고리 생성
        ExtractableResponse<Response> createResponse = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .body(body)
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
