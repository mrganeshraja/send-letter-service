package uk.gov.hmcts.reform.sendletter.dsl;

import com.typesafe.config.Config;

public class TestDsl {

    private final Config config;

    private final S2sApi s2sApi;

    private TestDsl(Config config, S2sApi s2sApi) {
        this.config = config;
        this.s2sApi = s2sApi;
    }

    public static TestDsl getInstance(Config config) {
        return new TestDsl(config, new S2sApi(config));
    }

    public LoggedInTestDsl login() {
        return new LoggedInTestDsl(
            config,
            s2sApi.signIn()
        );
    }

    public FtpTestDsl getFtpDsl() {
        return new FtpTestDsl(config);
    }
}
