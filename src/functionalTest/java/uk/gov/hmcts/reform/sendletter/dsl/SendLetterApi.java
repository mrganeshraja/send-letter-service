package uk.gov.hmcts.reform.sendletter.dsl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.OK;

class SendLetterApi implements Template {

    private final String sendLetterServiceUrl;

    SendLetterApi(ConfigWrapper config) {
        this.sendLetterServiceUrl = config.getString("send-letter-service-url");
    }

    String sendPrintLetterRequest(String jwt, String contentType, byte[] jsonBody) {
        return getNewSpecification()
            .header("ServiceAuthorization", "Bearer " + jwt)
            .header(CONTENT_TYPE, contentType)
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

    byte[] samplePdfLetterRequestJson(String requestBodyFilename) throws IOException {
        String requestBody = Resources.toString(getResource(requestBodyFilename), Charsets.UTF_8);
        byte[] pdf = toByteArray(getResource("test.pdf"));

        return requestBody.replace("{{pdf}}", new String(Base64.getEncoder().encode(pdf))).getBytes();
    }
}
