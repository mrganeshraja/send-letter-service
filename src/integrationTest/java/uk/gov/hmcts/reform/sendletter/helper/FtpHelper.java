package uk.gov.hmcts.reform.sendletter.helper;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import net.schmizz.sshj.SSHClient;
import uk.gov.hmcts.reform.sendletter.config.FtpConfigProperties;
import uk.gov.hmcts.reform.sendletter.config.FtpConfigProperties.ServiceFolderMapping;
import uk.gov.hmcts.reform.sendletter.logging.AppInsights;
import uk.gov.hmcts.reform.sendletter.services.LocalSftpServer;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpClient;

import java.io.IOException;
import java.util.function.Supplier;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.mock;

public final class FtpHelper {

    // Prevent instantiation.
    private FtpHelper() {
    }

    // Instantiate an FtpClient with host key verification disabled,
    // so it will connect to a local ftp server without verifying the
    // server's public key.
    private static FtpClient getClient(int port, boolean verified) throws IOException {
        Supplier<SSHClient> s = () -> {
            SSHClient client = new SSHClient();
            client.addHostKeyVerifier((a, b, c) -> verified);
            return client;
        };
        return new FtpClient(s, getFtpConfig(port), mock(AppInsights.class));
    }

    public static FtpClient getFailingClient(int port) throws IOException {
        return getClient(port, false);
    }

    public static FtpClient getSuccessfulClient(int port) throws IOException {
        return getClient(port, true);
    }

    private static FtpConfigProperties getFtpConfig(int port) throws IOException {
        FtpConfigProperties p = new FtpConfigProperties();
        p.setHostname("localhost");
        p.setPort(port);
        p.setPublicKey(Resources.toString(Resources.getResource("keypair.pub"), Charsets.UTF_8));
        p.setPrivateKey(Resources.toString(Resources.getResource("keypair"), Charsets.UTF_8));
        p.setUsername("irrelevant");
        p.setFingerprint("SHA1:2Fo8c/96zv32xc8GZWbOGYOlRak=");
        p.setTargetFolder(LocalSftpServer.LETTERS_FOLDER_NAME);
        p.setReportsFolder(LocalSftpServer.REPORT_FOLDER_NAME);
        ServiceFolderMapping serviceFolderMapping = new ServiceFolderMapping();
        serviceFolderMapping.setService("bulkprint");
        serviceFolderMapping.setFolder("BULKPRINT");
        p.setServiceFolders(singletonList(serviceFolderMapping));
        return p;
    }
}
