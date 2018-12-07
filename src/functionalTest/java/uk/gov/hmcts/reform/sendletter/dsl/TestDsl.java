package uk.gov.hmcts.reform.sendletter.dsl;

public class TestDsl {

    private final ConfigWrapper config;

    private final S2sApi s2sApi;

    private TestDsl(ConfigWrapper config, S2sApi s2sApi) {
        this.config = config;
        this.s2sApi = s2sApi;
    }

    public static TestDsl getInstance() {
        ConfigWrapper config = ConfigWrapper.load();

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
