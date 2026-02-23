package gift.cucumber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import gift.support.DatabaseCleaner;
import io.cucumber.java.Before;
import io.restassured.RestAssured;

public class CucumberHooks {

    @LocalServerPort
    int port;

    @Autowired
    DatabaseCleaner databaseCleaner;

    @Before
    public void setUp() {
        RestAssured.port = port;
        databaseCleaner.clear();
    }
}
