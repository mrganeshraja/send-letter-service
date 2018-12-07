package uk.gov.hmcts.reform.sendletter.dsl;

import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;
import java.util.Date;

import static org.apache.commons.lang.time.DateUtils.addMilliseconds;
import static org.assertj.core.util.DateUtil.now;

public class FtpTestDsl {

    private final ConfigWrapper config;

    private final Ftp ftp;

    private final Integer maxWaitForFtpFileInMs;

    private RemoteResourceInfo remoteResourceInfo = null;

    FtpTestDsl(ConfigWrapper config) {
        this.config = config;
        this.ftp = new Ftp(config);
        this.maxWaitForFtpFileInMs = config.getInt("max-wait-for-ftp-file-in-ms");
    }

    public SFTPClient getSftpClient() throws IOException {
        return ftp.getSftpClient();
    }

    public FtpTestDsl waitForFileOnSftp(SFTPClient sftp, String letterId) throws IOException, InterruptedException {
        Date waitUntil = addMilliseconds(now(), maxWaitForFtpFileInMs);

        remoteResourceInfo = ftp.waitForFile(waitUntil, sftp, letterId);

        return this;
    }

    public void validate(SFTPClient sftp, String letterId, int numberOfPages) throws IOException {
        FtpFileValidator validator = new FtpFileValidator(config, letterId);
        PdfFile pdfFile = validator.isEncryptionEnabled()
            ? ftp.processZipFile(sftp, remoteResourceInfo)
            : PdfFile.empty();

        validator
            .assertFileNameMatch(remoteResourceInfo.getName(), validator::getFtpFileNamePattern)
            .assertFileNameMatch(pdfFile.name, validator::getPdfFileNamePattern)
            .assertNumberOfPages(pdfFile.content, numberOfPages);
    }
}
