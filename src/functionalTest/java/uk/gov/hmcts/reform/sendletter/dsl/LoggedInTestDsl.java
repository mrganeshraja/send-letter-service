package uk.gov.hmcts.reform.sendletter.dsl;

import com.typesafe.config.Config;

import java.io.IOException;

public class LoggedInTestDsl {

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws IOException;
    }

    private final SendLetterApi sendLetterApi;

    private final String s2sToken;

    private ThrowingSupplier<byte[]> requestBody = () -> null;

    LoggedInTestDsl(Config config, String s2sToken) {
        this.sendLetterApi = new SendLetterApi(config);
        this.s2sToken = s2sToken;
    }

    public String sendLetter() throws IOException {
        return sendLetterApi.sendPrintLetterRequest(s2sToken, requestBody.get());
    }

    public LoggedInTestDsl withBodyTemplate(
        String requestBodyFilename,
        String templateFilename
    ) {
        requestBody = () -> sendLetterApi.sampleLetterRequestJson(requestBodyFilename, templateFilename);

        return this;
    }
}
