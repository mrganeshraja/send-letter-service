package uk.gov.hmcts.reform.sendletter.dsl;

import uk.gov.hmcts.reform.sendletter.controllers.MediaTypes;

import java.io.IOException;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class LoggedInTestDsl {

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws IOException;
    }

    private final SendLetterApi sendLetterApi;

    private final String s2sToken;

    private String contentType = APPLICATION_JSON_VALUE;

    private ThrowingSupplier<byte[]> requestBody = () -> null;

    LoggedInTestDsl(ConfigWrapper config, String s2sToken) {
        this.sendLetterApi = new SendLetterApi(config);
        this.s2sToken = s2sToken;
    }

    public String sendLetter() throws IOException {
        return sendLetterApi.sendPrintLetterRequest(s2sToken, contentType, requestBody.get());
    }

    public LoggedInTestDsl withBodyTemplate(
        String requestBodyFilename,
        String templateFilename
    ) {
        contentType = MediaTypes.LETTER_V1;
        requestBody = () -> sendLetterApi.sampleLetterRequestJson(requestBodyFilename, templateFilename);

        return this;
    }

    public LoggedInTestDsl withPdfBody(
        String requestBodyFilename
    ) {
        contentType = MediaTypes.LETTER_V2;
        requestBody = () -> sendLetterApi.samplePdfLetterRequestJson(requestBodyFilename);

        return this;
    }
}
