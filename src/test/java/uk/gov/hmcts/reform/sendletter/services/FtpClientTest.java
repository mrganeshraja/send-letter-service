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
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.hmcts.reform.sendletter.config.FtpConfigProperties;
import uk.gov.hmcts.reform.sendletter.exception.FtpException;
import uk.gov.hmcts.reform.sendletter.exception.ServiceNotConfiguredException;
import uk.gov.hmcts.reform.sendletter.logging.AppInsights;
import uk.gov.hmcts.reform.sendletter.model.Report;
import uk.gov.hmcts.reform.sendletter.services.ftp.FileToSend;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.doNothing;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class FtpClientTest {

    private FtpClient client;

    @Mock private SSHClient sshClient;
    @Mock private SFTPClient sftpClient;
    @Mock private SFTPFileTransfer sftpFileTransfer;
    @Mock private FtpConfigProperties ftpProps;
    @Mock private AppInsights insights;

    @Before
    public void setUp() throws Exception {
        given(sshClient.newSFTPClient()).willReturn(sftpClient);
        given(sftpClient.getFileTransfer()).willReturn(sftpFileTransfer);

        client = new FtpClient(() -> sshClient, ftpProps, insights);
    }

    @Test
    public void download_should_not_include_non_csv_files() throws Exception {
        // given
        RemoteResourceInfo nonCsvFile = mock(RemoteResourceInfo.class);
        given(nonCsvFile.isRegularFile()).willReturn(true);
        given(nonCsvFile.getName()).willReturn("test-report.pdf");
        given(ftpProps.getReportsFolder()).willReturn("reports");
        given(sftpClient.ls(anyString()))
            .willReturn(singletonList(nonCsvFile));

        // when
        List<Report> reports = client.downloadReports();

        // then
        assertThat(reports).isEmpty();
    }

    @Test
    public void should_track_failure_when_trying_to_download_report() throws IOException {
        // given
        willThrow(IOException.class).given(sftpClient).ls(eq(null));

        // when
        Throwable exception = catchThrowable(() -> client.downloadReports());

        // then
        assertThat(exception).isInstanceOf(FtpException.class);
    }

    @Test
    public void should_upload_file_to_correct_folder_based_on_whether_its_a_smoke_test_or_not() throws Exception {
        given(ftpProps.getSmokeTestTargetFolder()).willReturn("smoke");
        given(ftpProps.getTargetFolder()).willReturn("regular");

        Map<String, String> serviceFolderMappings = new HashMap<>();
        serviceFolderMappings.put("cmc", "CMC");
        given(ftpProps.getServiceFolders()).willReturn(serviceFolderMappings);

        // when
        client.upload(new FileToSend("hello.zip", "hello".getBytes()), true, "cmc");

        // then
        verify(sftpFileTransfer)
            .upload(
                any(LocalSourceFile.class),
                contains("smoke") //path
            );

        // when
        client.upload(new FileToSend("hello.zip", "hello".getBytes()), false, "cmc");

        // then
        verify(sftpFileTransfer)
            .upload(
                any(LocalSourceFile.class),
                contains("regular") //path
            );
    }

    @Test
    public void should_track_failure_when_uploading_file_for_unconfigured_service() {
        //given
        given(ftpProps.getServiceFolders()).willReturn(emptyMap());

        // when
        Throwable exception = catchThrowable(() ->
            client.upload(new FileToSend("goodbye.zip", "goodbye".getBytes()), false, "unconfigured-service")
        );

        // then
        assertThat(exception)
            .isInstanceOf(ServiceNotConfiguredException.class)
            .hasMessage("Service unconfigured-service is not configured to use bulk-print");
    }

    @Test
    public void should_track_failure_when_uploading_file_for_service_configured_with_empty_folder_name() {
        //given
        Map<String, String> serviceFolderMappings = new HashMap<>();
        serviceFolderMappings.put("unconfigured-service", "");
        given(ftpProps.getServiceFolders()).willReturn(serviceFolderMappings);

        // when
        Throwable exception = catchThrowable(() ->
            client.upload(new FileToSend("goodbye.zip", "goodbye".getBytes()), false, "unconfigured-service")
        );

        // then
        assertThat(exception)
            .isInstanceOf(ServiceNotConfiguredException.class)
            .hasMessage("Service unconfigured-service is not configured to use bulk-print");
    }

    @Test
    public void should_track_failure_when_trying_to_upload_new_file() throws IOException {
        // given
        Map<String, String> serviceFolderMappings = new HashMap<>();
        serviceFolderMappings.put("cmc", "CMC");
        given(ftpProps.getServiceFolders()).willReturn(serviceFolderMappings);

        willThrow(IOException.class).given(sftpFileTransfer).upload(any(LocalSourceFile.class), anyString());

        // when
        Throwable exception = catchThrowable(() ->
            client.upload(new FileToSend("goodbye.zip", "goodbye".getBytes()), false, "cmc")
        );

        // then
        assertThat(exception).isInstanceOf(FtpException.class);
    }

    @Test
    public void should_delete_report_from_ftp() throws IOException {
        // given
        doNothing().when(sftpClient).rm(anyString());

        // when
        Throwable exception = catchThrowable(() -> client.deleteReport("some/report"));

        // then
        assertThat(exception).isNull();
    }

    @Test
    public void should_track_failure_when_trying_to_delete_report_from_ftp() throws IOException {
        // given
        willThrow(IOException.class).given(sftpClient).rm(anyString());

        // when
        Throwable exception = catchThrowable(() -> client.deleteReport("some/report"));

        // then
        assertThat(exception).isInstanceOf(FtpException.class);
    }
}
