package uk.gov.hmcts.reform.sendletter.dsl;

import com.typesafe.config.Config;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.util.DateUtil.now;

class Ftp {

    private final String ftpHostname;

    private final Integer ftpPort;

    private final String ftpFingerprint;

    private final String ftpTargetFolder;

    private final String ftpUser;

    private final String ftpPrivateKey;

    private final String ftpPublicKey;

    Ftp(Config config) {
        this.ftpHostname = config.getString("ftp-hostname");
        this.ftpPort = config.getInt("ftp-port");
        this.ftpFingerprint = config.getString("ftp-fingerprint");
        this.ftpTargetFolder = config.getString("ftp-target-folder");
        this.ftpUser = config.getString("ftp-user");
        this.ftpPrivateKey = config.getString("ftp-private-key");
        this.ftpPublicKey = config.getString("ftp-public-key");
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

    RemoteResourceInfo waitForFile(
        Date waitUntil,
        SFTPClient sftp,
        String letterId
    ) throws IOException, InterruptedException {
        List<RemoteResourceInfo> matchingFiles;

        while (!now().after(waitUntil)) {
            matchingFiles = sftp.ls(ftpTargetFolder, file -> file.getName().contains(letterId));

            if (matchingFiles.size() == 1) {
                return matchingFiles.get(0);
            } else if (matchingFiles.size() > 1) {
                String failMessage = String.format(
                    "Expected one file with name containing '%s'. Found %d",
                    letterId,
                    matchingFiles.size()
                );

                fail(failMessage);
            } else {
                Thread.sleep(1000);
            }
        }

        throw new AssertionError("The expected file didn't appear on SFTP server");
    }
}
