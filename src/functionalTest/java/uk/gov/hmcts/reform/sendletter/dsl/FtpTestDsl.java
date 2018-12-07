package uk.gov.hmcts.reform.sendletter.dsl;

import com.typesafe.config.Config;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;
import java.util.Date;

import static org.apache.commons.lang.time.DateUtils.addMilliseconds;
import static org.assertj.core.util.DateUtil.now;

public class FtpTestDsl {

    private final Ftp ftp;

    private final int maxWaitForFtpFileInMs;

    RemoteResourceInfo remoteResourceInfo = null;

    FtpTestDsl(Config config) {
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
}
