package uk.gov.hmcts.reform.sendletter.services.ftp;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPFileTransfer;
import net.schmizz.sshj.xfer.LocalSourceFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sendletter.config.FtpConfigProperties;
import uk.gov.hmcts.reform.sendletter.exception.FtpException;
import uk.gov.hmcts.reform.sendletter.model.InMemoryDownloadedFile;
import uk.gov.hmcts.reform.sendletter.model.Report;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toList;

@Component
@EnableConfigurationProperties(FtpConfigProperties.class)
public class FtpClient {

    private static final Logger logger = LoggerFactory.getLogger(FtpClient.class);

    private final FtpConfigProperties configProperties;
    private final Supplier<SSHClient> sshClientSupplier;

    private SSHClient sshClient;
    private SFTPClient sftpClient;

    // region constructor
    public FtpClient(
        Supplier<SSHClient> sshClientSupplier,
        FtpConfigProperties configProperties
    ) {
        this.sshClientSupplier = sshClientSupplier;
        this.configProperties = configProperties;
    }
    // endregion

    public void upload(LocalSourceFile file, boolean isSmokeTestFile) {
        assertConnected();
        try {
            String folder = isSmokeTestFile
                ? configProperties.getSmokeTestTargetFolder()
                : configProperties.getTargetFolder();

            String path = String.join("/", folder, file.getName());
            this.sftpClient.getFileTransfer().upload(file, path);

        } catch (IOException exc) {
            throw new FtpException("Unable to upload file.", exc);
        }
    }

    /**
     * Downloads ALL files from reports directory.
     */
    public List<Report> downloadReports() {
        assertConnected();
        try {
            SFTPFileTransfer transfer = sftpClient.getFileTransfer();

            return sftpClient
                .ls(configProperties.getReportsFolder())
                .stream()
                .filter(this::isReportFile)
                .map(file -> {
                    InMemoryDownloadedFile inMemoryFile = new InMemoryDownloadedFile();
                    try {
                        transfer.download(file.getPath(), inMemoryFile);
                        return new Report(file.getPath(), inMemoryFile.getBytes());
                    } catch (IOException exc) {
                        throw new FtpException("Unable to download file " + file.getName(), exc);
                    }
                })
                .collect(toList());
        } catch (IOException exc) {
            throw new FtpException("Error while downloading reports", exc);
        }
    }

    public void deleteReport(String reportPath) {
        try {
            this.sftpClient.rm(reportPath);
        } catch (Exception exc) {
            throw new FtpException("Error while deleting report: " + reportPath, exc);
        }
    }

    public void testConnection() {
        try {
            this.connect();
        } finally {
            this.disconnect();
        }
    }

    public void connect() {
        try {
            SSHClient ssh = sshClientSupplier.get();
            ssh.addHostKeyVerifier(configProperties.getFingerprint());
            ssh.connect(configProperties.getHostname(), configProperties.getPort());

            ssh.authPublickey(
                configProperties.getUsername(),
                ssh.loadKeys(
                    configProperties.getPrivateKey(),
                    configProperties.getPublicKey(),
                    null
                )
            );

            this.sshClient = ssh;
            this.sftpClient = ssh.newSFTPClient();

        } catch (IOException exc) {
            throw new FtpException("Unable to connect to SFTP.", exc);
        }
    }

    public void disconnect() {
        if (this.sshClient != null) {
            try {
                this.sshClient.disconnect();
                this.sshClient = null;
                this.sftpClient = null;
            } catch (IOException exc) {
                logger.warn("Error closing ssh connection.", exc);
            }
        }
    }

    private void assertConnected() {
        if (this.sftpClient == null) {
            throw new IllegalStateException("Not connected");
        }
    }

    private boolean isReportFile(RemoteResourceInfo resourceInfo) {
        return resourceInfo.isRegularFile()
            && resourceInfo.getName().toLowerCase(Locale.getDefault()).endsWith(".csv");
    }
}
