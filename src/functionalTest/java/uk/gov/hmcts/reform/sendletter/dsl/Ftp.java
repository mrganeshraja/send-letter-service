package uk.gov.hmcts.reform.sendletter.dsl;

import com.typesafe.config.Config;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;

class Ftp {

    private final String ftpHostname;

    private final Integer ftpPort;

    private final String ftpFingerprint;

    private final String ftpTargetFolder;

    private final String ftpUser;

    private final String ftpPrivateKey;

    private final String ftpPublicKey;

    private final int maxWaitForFtpFileInMs;

    Ftp(Config config) {
        this.ftpHostname = config.getString("ftp-hostname");
        this.ftpPort = config.getInt("ftp-port");
        this.ftpFingerprint = config.getString("ftp-fingerprint");
        this.ftpTargetFolder = config.getString("ftp-target-folder");
        this.ftpUser = config.getString("ftp-user");
        this.ftpPrivateKey = config.getString("ftp-private-key");
        this.ftpPublicKey = config.getString("ftp-public-key");
        this.maxWaitForFtpFileInMs = config.getInt("max-wait-for-ftp-file-in-ms");
    }

    SFTPClient getSftpClient() throws IOException {
        SSHClient ssh = new SSHClient();

        ssh.addHostKeyVerifier(ftpFingerprint);
        ssh.connect(ftpHostname, ftpPort);

        ssh.authPublickey(
            ftpUser,
            ssh.loadKeys(ftpPrivateKey, ftpPublicKey, null)
        );

        return ssh.newSFTPClient();
    }
}
