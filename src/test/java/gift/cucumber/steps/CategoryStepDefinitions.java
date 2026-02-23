package gift.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import gift.cucumber.ScenarioContext;
import gift.fixture.CategoryFixture;
import gift.support.TestDataInitializer;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public class CategoryStepDefinitions {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private TestDataInitializer initializer;

    @Given("카테고리가 등록되어 있다")
    public void 카테고리가_등록되어_있다() {
        Long categoryId = initializer.saveCategory(CategoryFixture.기본카테고리());
        context.setCategoryId(categoryId);
    }

    @When("{string} 카테고리를 등록한다")
    public void 카테고리를_등록한다(String name) {
        String body = """
                {
                    "name": "%s"
                }
                """.formatted(name);

        var response = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .body(body)
                .when()
                .post("/api/categories")
                .then().log().all()
                .extract();

        context.setResponse(response);
        if (response.statusCode() == 200) {
            context.setCategoryId(response.jsonPath().getLong("id"));
        }
    }

    @Then("카테고리 등록이 성공한다")
    public void 카테고리_등록이_성공한다() {
        assertThat(context.getStatusCode()).isEqualTo(200);
    }

    @Then("카테고리 목록에 {string}이 포함되어 있다")
    public void 카테고리_목록에_이름이_포함되어_있다(String name) {
        var response = RestAssured.given().log().all()
                .when()
                .get("/api/categories")
                .then().log().all()
                .extract();

        List<Long> ids = response.jsonPath().getList("id", Long.class);
        assertThat(ids).containsExactly(context.getCategoryId());

        List<String> names = response.jsonPath().getList("name", String.class);
        assertThat(names).containsExactly(name);
    }
}
