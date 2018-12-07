package uk.gov.hmcts.reform.sendletter;

import net.schmizz.sshj.sftp.RemoteFile;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.sendletter.dsl.FtpTestDsl;
import uk.gov.hmcts.reform.sendletter.dsl.TestDsl;

import java.io.IOException;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class ProcessMessageTest extends FunctionalTestSuite {

    private TestDsl dsl;
    private FtpTestDsl ftpDsl;

    @Override
    void initDsl() {
        dsl = TestDsl.getInstance(config);
        ftpDsl = dsl.getFtpDsl();
    }

    static Stream<Arguments> templateProvider() {
        return Stream.of(
            arguments("letter_single_document.json", "two-page-template.html", 2),
            arguments("letter_single_document.json", "one-page-template.html", 2),
            arguments("letter_two_documents.json", "two-page-template.html", 4),
            arguments("letter_two_documents.json", "one-page-template.html", 4)
        );
    }

    @DisplayName("Should send letter and upload file to SFTP server")
    @ParameterizedTest
    @MethodSource("templateProvider")
    public void sendLetterWithTemplate(
        String requestBodyFilename,
        String templateFilename,
        int numberOfDocuments
    ) throws Exception {
        String letterId = dsl
            .login()
            .withBodyTemplate(requestBodyFilename, templateFilename)
            .sendLetter();

        try (SFTPClient sftp = ftpDsl.getSftpClient()) {
            ftpDsl
                .waitForFileOnSftp(sftp, letterId);

            assertThat(sftpFile.getName()).matches(getFileNamePattern(letterId));

            if (!isEncryptionEnabled) {
                validatePdfFile(letterId, sftp, sftpFile, numberOfDocuments);
            }
        }
    }

    private void validatePdfFile(String letterId, SFTPClient sftp, RemoteResourceInfo sftpFile, int noOfDocuments)
        throws IOException {
        try (RemoteFile zipFile = sftp.open(sftpFile.getPath())) {
            PdfFile pdfFile = unzipFile(zipFile);
            assertThat(pdfFile.name).matches(getPdfFileNamePattern(letterId));

            PDDocument pdfDocument = PDDocument.load(pdfFile.content);
            assertThat(pdfDocument.getNumberOfPages()).isEqualTo(noOfDocuments);
        }
    }

    private PdfFile unzipFile(RemoteFile zipFile) throws IOException {
        try (ZipInputStream zipStream = getZipInputStream(zipFile)) {
            ZipEntry firstEntry = zipStream.getNextEntry();
            byte[] pdfContent = readAllBytes(zipStream);

            ZipEntry secondEntry = zipStream.getNextEntry();
            assertThat(secondEntry).as("second file in zip").isNull();

            String pdfName = firstEntry.getName();

            return new PdfFile(pdfName, pdfContent);
        }
    }

    private static class PdfFile {
        public final String name;
        public final byte[] content;

        public PdfFile(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }
}
