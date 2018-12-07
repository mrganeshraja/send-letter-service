package uk.gov.hmcts.reform.sendletter.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import java.io.IOException;
import java.util.Iterator;

import static com.google.common.io.Resources.getResource;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

class SendLetterApi implements Template {

    private final String sendLetterServiceUrl;

    SendLetterApi(ConfigWrapper config) {
        this.sendLetterServiceUrl = config.getString("send-letter-service-url");
    }

    String sendPrintLetterRequest(String jwt, byte[] jsonBody) {
        return getNewSpecification()
            .header("ServiceAuthorization", "Bearer " + jwt)
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .baseUri(this.sendLetterServiceUrl)
            .body(jsonBody)
            .when()
            .post("/letters")
            .then()
            .statusCode(OK.value())
            .extract()
            .body()
            .jsonPath()
            .get("letter_id");
    }

    byte[] sampleLetterRequestJson(String requestBodyFilename, String templateFilename) throws IOException {
        String template = Resources.toString(getResource(templateFilename), Charsets.UTF_8);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode requestBody = mapper.readTree(getResource(requestBodyFilename));
        Iterator<JsonNode> documents = requestBody.get("documents").elements();

        while (documents.hasNext()) {
            ((ObjectNode) documents.next()).put("template", template);
        }

        return requestBody.toString().getBytes();
    }
}
