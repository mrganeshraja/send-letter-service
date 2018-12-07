package uk.gov.hmcts.reform.sendletter;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.SFTPClient;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;

public abstract class FunctionalTestSuite {

    @Value("${s2s-name}")
    protected String s2sName;

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

    static final Config config = ConfigFactory.load();

    @BeforeEach
    public void setUp() {
        initDsl();
    }

    abstract void initDsl();

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
}
