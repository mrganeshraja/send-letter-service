package uk.gov.hmcts.reform.sendletter.services;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPFileTransfer;
import net.schmizz.sshj.xfer.LocalSourceFile;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.hmcts.reform.sendletter.config.FtpConfigProperties;
import uk.gov.hmcts.reform.sendletter.exception.FtpException;
import uk.gov.hmcts.reform.sendletter.model.Report;
import uk.gov.hmcts.reform.sendletter.services.ftp.FileToSend;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpClient;

import java.io.IOException;
import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.doNothing;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class FtpClientTest {

    private FtpClient client;

    @Mock private SSHClient sshClient;
    @Mock private SFTPClient sftpClient;
    @Mock private SFTPFileTransfer sftpFileTransfer;
    @Mock private FtpConfigProperties ftpProps;

    @Before
    public void setUp() throws Exception {
        given(sshClient.newSFTPClient()).willReturn(sftpClient);
        given(sftpClient.getFileTransfer()).willReturn(sftpFileTransfer);

        client = new FtpClient(() -> sshClient, ftpProps);
    }

    @Test
    public void download_should_not_include_non_csv_files() throws Exception {
        // given
        RemoteResourceInfo nonCsvFile = mock(RemoteResourceInfo.class);
        given(nonCsvFile.isRegularFile()).willReturn(true);
        given(nonCsvFile.getName()).willReturn("test-report.pdf");

        given(sftpClient.ls(anyString()))
            .willReturn(singletonList(nonCsvFile));

        // when
        client.connect();
        List<Report> reports = client.downloadReports();
        client.disconnect();

        // then
        assertThat(reports).isEmpty();
    }

    @Test
    public void should_track_failure_when_trying_to_download_report() throws IOException {
        // given
        willThrow(IOException.class).given(sftpClient).ls(anyString());

        // when
        Throwable exception = catchThrowable(() -> {
            client.connect();
            client.downloadReports();
            client.disconnect();
        });

        // then
        assertThat(exception).isInstanceOf(FtpException.class);
    }

    @Test
    public void should_upload_file_to_correct_folder_based_on_whether_its_a_smoke_test_or_not() throws Exception {
        given(ftpProps.getSmokeTestTargetFolder()).willReturn("smoke");
        given(ftpProps.getTargetFolder()).willReturn("regular");

        client.connect();

        // when
        client.upload(new FileToSend("hello.zip", "hello".getBytes()), true);

        // then
        verify(sftpFileTransfer)
            .upload(
                any(LocalSourceFile.class),
                contains("smoke") //path
            );

        // when
        client.connect();
        client.upload(new FileToSend("hello.zip", "hello".getBytes()), false);
        client.disconnect();

        // then
        verify(sftpFileTransfer)
            .upload(
                any(LocalSourceFile.class),
                contains("regular") //path
            );
    }

    @Test
    public void should_track_failure_when_trying_to_upload_new_file() throws IOException {
        // given
        willThrow(IOException.class).given(sftpFileTransfer).upload(any(LocalSourceFile.class), anyString());

        // when
        Throwable exception = catchThrowable(() -> {
            client.connect();
            client.upload(new FileToSend("goodbye.zip", "goodbye".getBytes()), false);
            client.disconnect();
        });

        // then
        assertThat(exception).isInstanceOf(FtpException.class);
    }

    @Test
    public void should_delete_report_from_ftp() throws IOException {
        // given
        doNothing().when(sftpClient).rm(anyString());

        // when
        Throwable exception = catchThrowable(() -> {
            client.connect();
            client.deleteReport("some/report");
            client.disconnect();
        });

        // then
        assertThat(exception).isNull();
    }

    @Test
    public void should_track_failure_when_trying_to_delete_report_from_ftp() throws IOException {
        // given
        willThrow(IOException.class).given(sftpClient).rm(anyString());

        // when
        Throwable exception = catchThrowable(() -> {
            client.connect();
            client.deleteReport("some/report");
            client.disconnect();
        });

        // then
        assertThat(exception).isInstanceOf(FtpException.class);
    }
}
