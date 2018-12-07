package uk.gov.hmcts.reform.sendletter.dsl;

import com.typesafe.config.Config;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;

public class FtpTestDsl {

    private final Ftp ftp;

    FtpTestDsl(Config config) {
        this.ftp = new Ftp(config);
    }

    public SFTPClient getSftpClient() throws IOException {
        return ftp.getSftpClient();
    }
}
