package uk.gov.hmcts.reform.sendletter.dsl;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
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

    Ftp(ConfigWrapper config) {
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

    PdfFile processZipFile(SFTPClient sftp, RemoteResourceInfo remoteResourceInfo) throws IOException {
        try (RemoteFile zipFile = sftp.open(remoteResourceInfo.getPath())) {
            return unzipFile(zipFile);
        }
    }

    private PdfFile unzipFile(RemoteFile zipFile) throws IOException {
        try (ZipInputStream zipStream = getZipInputStream(zipFile)) {
            ZipEntry firstEntry = zipStream.getNextEntry();
            byte[] pdfContent = readAllBytes(zipStream);

            ZipEntry secondEntry = zipStream.getNextEntry();
            assertThat(secondEntry).as("second file in zip").isNull();

            return new PdfFile(firstEntry.getName(), pdfContent);
        }
    }

    private ZipInputStream getZipInputStream(RemoteFile zipFile) throws IOException {
        byte[] fileContent = new byte[(int) zipFile.length()];
        zipFile.read(0, fileContent, 0, (int) zipFile.length());

        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent, 0, fileContent.length);

        return new ZipInputStream(inputStream);
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[10000];
        int len;

        while ((len = input.read(buffer)) > 0) {
            output.write(buffer, 0, len);
        }

        return output.toByteArray();
    }
}
