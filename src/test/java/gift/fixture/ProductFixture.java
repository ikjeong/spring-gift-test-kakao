package gift.fixture;

import gift.model.Product;

public class ProductFixture {

    public static Product 기본상품() {
        return new Product("아이스 아메리카노", 4500, "https://example.com/image.png", null);
    }
}
