package gift.support;

import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseCleaner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    public void clear() {
        if ("PostgreSQL".equalsIgnoreCase(getDatabaseProductName())) {
            jdbcTemplate.execute(
                "TRUNCATE TABLE wish, option, product, member, category RESTART IDENTITY CASCADE"
            );
        } else {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("TRUNCATE TABLE wish");
            jdbcTemplate.execute("TRUNCATE TABLE option");
            jdbcTemplate.execute("TRUNCATE TABLE product");
            jdbcTemplate.execute("TRUNCATE TABLE member");
            jdbcTemplate.execute("TRUNCATE TABLE category");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private String getDatabaseProductName() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
