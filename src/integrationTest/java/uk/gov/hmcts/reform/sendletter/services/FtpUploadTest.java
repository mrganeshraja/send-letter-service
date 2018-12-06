package uk.gov.hmcts.reform.sendletter.services;

import com.google.common.io.Files;
import org.junit.Test;
import uk.gov.hmcts.reform.sendletter.exception.ServiceNotConfiguredException;
import uk.gov.hmcts.reform.sendletter.helper.FtpHelper;
import uk.gov.hmcts.reform.sendletter.services.ftp.FileToSend;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpClient;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class FtpUploadTest {

    @Test
    public void connects_to_ftp() throws Exception {
        try (LocalSftpServer server = LocalSftpServer.create()) {
            FtpHelper.getSuccessfulClient(LocalSftpServer.port).testConnection();
        }
    }

    @Test
    public void uploads_file() throws Exception {
        FileToSend doc = new FileToSend("hello.zip", "world".getBytes());
        try (LocalSftpServer server = LocalSftpServer.create()) {
            FtpClient client = FtpHelper.getSuccessfulClient(LocalSftpServer.port);
            client.upload(doc, false, "bulkprint");
            File[] files = server.lettersFolder.listFiles();
            assertThat(files.length).isEqualTo(1);
            String content = new String(Files.toByteArray(files[0]));
            assertThat(content).isEqualTo("world");
        }
    }

    @Test
    public void should_not_upload_file_when_service_is_not_configured() throws Exception {
        //given
        FileToSend doc = new FileToSend("hello.zip", "world".getBytes());

        try (LocalSftpServer server = LocalSftpServer.create()) {
            FtpClient client = FtpHelper.getSuccessfulClient(LocalSftpServer.port);

            Throwable thrown = catchThrowable(() -> client.upload(doc, false, "unconfigured-service"));

            //then
            assertThat(thrown)
                .isInstanceOf(ServiceNotConfiguredException.class)
                .hasMessage("Service unconfigured-service is not configured to use bulk-print");
        }
    }
}
