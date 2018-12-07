package uk.gov.hmcts.reform.sendletter.dsl;

import com.google.common.collect.ImmutableMap;
import com.warrenstrange.googleauth.GoogleAuthenticator;

import java.util.Map;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

class S2sApi implements Template {

    private final String s2sUrl;

    private final String s2sName;

    private final String s2sSecret;

    S2sApi(ConfigWrapper config) {
        this.s2sUrl = config.getString("s2s-url");
        this.s2sName = config.getString("s2s-name");
        this.s2sSecret = config.getString("s2s-secret");
    }

    String signIn() {
        Map<String, Object> params = ImmutableMap.of(
            "microservice", this.s2sName,
            "oneTimePassword", new GoogleAuthenticator().getTotpPassword(this.s2sSecret)
        );

        return getNewSpecification()
            .baseUri(this.s2sUrl)
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .body(params)
            .when()
            .post("/lease")
            .then()
            .statusCode(OK.value())
            .and()
            .extract()
            .response()
            .body()
            .print();
    }
}
