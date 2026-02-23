package gift.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import gift.cucumber.ScenarioContext;
import gift.fixture.MemberFixture;
import gift.fixture.OptionFixture;
import gift.fixture.ProductFixture;
import gift.support.TestDataInitializer;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public class GiftStepDefinitions {

    @Autowired
    private ScenarioContext context;

    @Autowired
    private TestDataInitializer initializer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Given("발신 회원과 수신 회원이 등록되어 있다")
    public void 발신_회원과_수신_회원이_등록되어_있다() {
        context.setSenderId(initializer.saveMember(MemberFixture.발신회원()));
        context.setReceiverId(initializer.saveMember(MemberFixture.수신회원()));
    }

    @Given("재고 {int}개인 옵션을 가진 상품이 등록되어 있다")
    public void 재고_n개인_옵션을_가진_상품이_등록되어_있다(int quantity) {
        Long productId = initializer.saveProduct(ProductFixture.기본상품(), context.getCategoryId());
        context.setProductId(productId);

        var option = OptionFixture.기본옵션(quantity);
        Long optionId = initializer.saveOption(option, productId);
        context.setOptionId(optionId);
    }

    @When("발신 회원이 수신 회원에게 {int}개를 선물한다")
    public void 발신_회원이_수신_회원에게_n개를_선물한다(int quantity) {
        String body = """
                {
                    "optionId": %d,
                    "quantity": %d,
                    "receiverId": %d,
                    "message": "선물입니다"
                }
                """.formatted(context.getOptionId(), quantity, context.getReceiverId());

        var response = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", context.getSenderId())
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .extract();

        context.setResponse(response);
    }

    @When("존재하지 않는 옵션으로 선물한다")
    public void 존재하지_않는_옵션으로_선물한다() {
        String body = """
                {
                    "optionId": 999,
                    "quantity": 1,
                    "receiverId": %d,
                    "message": "선물입니다"
                }
                """.formatted(context.getReceiverId());

        var response = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", context.getSenderId())
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .extract();

        context.setResponse(response);
    }

    @When("존재하지 않는 발신자가 선물한다")
    public void 존재하지_않는_발신자가_선물한다() {
        String body = """
                {
                    "optionId": %d,
                    "quantity": 1,
                    "receiverId": %d,
                    "message": "선물입니다"
                }
                """.formatted(context.getOptionId(), context.getReceiverId());

        var response = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .header("Member-Id", 999L)
                .body(body)
                .when()
                .post("/api/gifts")
                .then().log().all()
                .extract();

        context.setResponse(response);
    }

    @Then("선물 발송이 성공한다")
    public void 선물_발송이_성공한다() {
        assertThat(context.getStatusCode()).isEqualTo(200);
    }

    @Then("선물 발송이 실패한다")
    public void 선물_발송이_실패한다() {
        assertThat(context.getStatusCode()).isEqualTo(500);
    }

    @Then("옵션 재고가 {int}개로 차감되어 있다")
    public void 옵션_재고가_n개로_차감되어_있다(int expectedQuantity) {
        Integer actualQuantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM option WHERE id = ?", Integer.class, context.getOptionId());
        assertThat(actualQuantity).isEqualTo(expectedQuantity);
    }

    @Then("옵션 재고가 {int}개로 유지되어 있다")
    public void 옵션_재고가_n개로_유지되어_있다(int expectedQuantity) {
        Integer actualQuantity = jdbcTemplate.queryForObject(
                "SELECT quantity FROM option WHERE id = ?", Integer.class, context.getOptionId());
        assertThat(actualQuantity).isEqualTo(expectedQuantity);
    }
}
