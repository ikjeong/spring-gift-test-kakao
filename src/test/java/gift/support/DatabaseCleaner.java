package gift.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void clear() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE wish, option, product, member, category RESTART IDENTITY CASCADE"
        );
    }
}
