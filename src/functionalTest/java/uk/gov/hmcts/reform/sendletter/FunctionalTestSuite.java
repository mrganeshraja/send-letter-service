package uk.gov.hmcts.reform.sendletter;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RunWith(SpringRunner.class)
@TestPropertySource("classpath:application.properties")
public abstract class FunctionalTestSuite {

    @Value("${s2s-url}")
    protected String s2sUrl;

    @Value("${s2s-name}")
    protected String s2sName;

    @Value("${s2s-secret}")
    protected String s2sSecret;

    @Value("${send-letter-service-url}")
    protected String sendLetterServiceUrl;

    @Value("${ftp-hostname}")
    protected String ftpHostname;

    @Value("${ftp-port}")
    protected Integer ftpPort;

    @Value("${ftp-fingerprint}")
    protected String ftpFingerprint;

    @Value("${ftp-target-folder}")
    protected String ftpTargetFolder;

    @Value("${ftp-user}")
    protected String ftpUser;

    @Value("${ftp-private-key}")
    protected String ftpPrivateKey;

    @Value("${ftp-public-key}")
    protected String ftpPublicKey;

    @Value("${max-wait-for-ftp-file-in-ms}")
    protected int maxWaitForFtpFileInMs;

    @Value("${encryption.enabled}")
    protected Boolean isEncryptionEnabled;

    /**
     * Sign in to s2s.
     *
     * @return s2s JWT token.
     */
    protected String signIn() {
        Map<String, Object> params = ImmutableMap.of(
            "microservice", this.s2sName,
            "oneTimePassword", new GoogleAuthenticator().getTotpPassword(this.s2sSecret)
        );

        Response response = RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(this.s2sUrl)
            .header(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .body(params)
            .when()
            .post("/lease")
            .andReturn();

        assertThat(response.getStatusCode()).isEqualTo(200);

        return response
            .getBody()
            .print();
    }

    protected String sendPrintLetterRequest(String jwt, String jsonBody) {
        return RestAssured
            .given()
            .relaxedHTTPSValidation()
            .header("ServiceAuthorization", "Bearer " + jwt)
            .header(CONTENT_TYPE, getContentType())
            .baseUri(sendLetterServiceUrl)
            .body(jsonBody.getBytes())
            .when()
            .post("/letters")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .jsonPath()
            .get("letter_id");
    }

    protected String sampleLetterRequestJson(
        String requestBodyFilename,
        String templateFilename
    ) throws IOException, JSONException {
        String requestBody = Resources.toString(getResource(requestBodyFilename), Charsets.UTF_8);
        String template = Resources.toString(getResource(templateFilename), Charsets.UTF_8);
        JSONObject object = new JSONObject(requestBody);
        JSONArray documents = object.getJSONArray("documents");

        for (int i = 0; i < documents.length(); i++) {
            documents.getJSONObject(i).put("template", template);
        }

        return object.toString();
    }

    protected String samplePdfLetterRequestJson(String requestBodyFilename) throws IOException {
        String requestBody = Resources.toString(getResource(requestBodyFilename), Charsets.UTF_8);
        byte[] pdf = toByteArray(getResource("test.pdf"));

        return requestBody.replace("{{pdf}}", new String(Base64.getEncoder().encode(pdf)));
    }

    protected SFTPClient getSftpClient() throws IOException {
        SSHClient ssh = new SSHClient();

        ssh.addHostKeyVerifier(ftpFingerprint);
        ssh.connect(ftpHostname, ftpPort);

        ssh.authPublickey(
            ftpUser,
            ssh.loadKeys(ftpPrivateKey, ftpPublicKey, null)
        );

        return ssh.newSFTPClient();
    }

    protected ZipInputStream getZipInputStream(RemoteFile zipFile) throws IOException {
        byte[] fileContent = new byte[(int) zipFile.length()];
        zipFile.read(0, fileContent, 0, (int) zipFile.length());

        ByteArrayInputStream inputStream =
            new ByteArrayInputStream(fileContent, 0, fileContent.length);

        return new ZipInputStream(inputStream);
    }

    protected byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[10000];
        int len;

        while ((len = input.read(buffer)) > 0) {
            output.write(buffer, 0, len);
        }

        return output.toByteArray();
    }

    protected String getPdfFileNamePattern(String letterId) {
        return String.format(
            "%s_%s_%s.pdf",
            Pattern.quote("smoke_test"),
            Pattern.quote(s2sName.replace("_", "")),
            Pattern.quote(letterId)
        );
    }

    protected String getFileNamePattern(String letterId) {
        String format = isEncryptionEnabled ? "%s_%s_\\d{14}_%s.pgp" : "%s_%s_\\d{14}_%s.zip";

        return String.format(
            format,
            Pattern.quote("smoketest"),
            Pattern.quote(s2sName.replace("_", "")),
            Pattern.quote(letterId)
        );
    }

    abstract String getContentType();
}
