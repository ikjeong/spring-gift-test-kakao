package gift.cucumber;

import org.springframework.stereotype.Component;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

@Component
@ScenarioScope
public class ScenarioContext {

    private ExtractableResponse<Response> response;
    private int statusCode;
    private Long categoryId;
    private Long productId;
    private Long optionId;
    private Long senderId;
    private Long receiverId;

    public ExtractableResponse<Response> getResponse() {
        return response;
    }

    public void setResponse(ExtractableResponse<Response> response) {
        this.response = response;
        this.statusCode = response.statusCode();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Long getOptionId() {
        return optionId;
    }

    public void setOptionId(Long optionId) {
        this.optionId = optionId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
    }
}
