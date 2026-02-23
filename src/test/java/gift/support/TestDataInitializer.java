package gift.support;

import gift.model.Category;
import gift.model.Member;
import gift.model.Option;
import gift.model.Product;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Map;

@Component
public class TestDataInitializer {

    private final SimpleJdbcInsert memberInsert;
    private final SimpleJdbcInsert categoryInsert;
    private final SimpleJdbcInsert productInsert;
    private final SimpleJdbcInsert optionInsert;

    public TestDataInitializer(DataSource dataSource) {
        this.memberInsert = new SimpleJdbcInsert(dataSource)
                .withTableName("member")
                .usingGeneratedKeyColumns("id");
        this.categoryInsert = new SimpleJdbcInsert(dataSource)
                .withTableName("category")
                .usingGeneratedKeyColumns("id");
        this.productInsert = new SimpleJdbcInsert(dataSource)
                .withTableName("product")
                .usingGeneratedKeyColumns("id");
        this.optionInsert = new SimpleJdbcInsert(dataSource)
                .withTableName("option")
                .usingGeneratedKeyColumns("id");
    }

    public Long saveMember(Member member) {
        Map<String, Object> params = Map.of(
                "name", member.getName(),
                "email", member.getEmail()
        );
        return memberInsert.executeAndReturnKey(params).longValue();
    }

    public Long saveCategory(Category category) {
        Map<String, Object> params = Map.of(
                "name", category.getName()
        );
        return categoryInsert.executeAndReturnKey(params).longValue();
    }

    public Long saveProduct(Product product, Long categoryId) {
        Map<String, Object> params = Map.of(
                "name", product.getName(),
                "price", product.getPrice(),
                "image_url", product.getImageUrl(),
                "category_id", categoryId
        );
        return productInsert.executeAndReturnKey(params).longValue();
    }

    public Long saveOption(Option option, Long productId) {
        Map<String, Object> params = Map.of(
                "name", option.getName(),
                "quantity", option.getQuantity(),
                "product_id", productId
        );
        return optionInsert.executeAndReturnKey(params).longValue();
    }
}
