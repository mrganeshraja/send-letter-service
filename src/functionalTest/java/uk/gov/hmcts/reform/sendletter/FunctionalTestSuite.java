package uk.gov.hmcts.reform.sendletter;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.BeforeEach;

public abstract class FunctionalTestSuite {

    static final Config config = ConfigFactory.load();

    @BeforeEach
    public void setUp() {
        initDsl();
    }

    abstract void initDsl();
}
