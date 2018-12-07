package uk.gov.hmcts.reform.sendletter;

import net.schmizz.sshj.sftp.SFTPClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.hmcts.reform.sendletter.dsl.FtpTestDsl;
import uk.gov.hmcts.reform.sendletter.dsl.TestDsl;

import java.io.IOException;
import java.util.stream.Stream;

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
    ) throws IOException, InterruptedException {
        String letterId = dsl
            .login()
            .withBodyTemplate(requestBodyFilename, templateFilename)
            .sendLetter();

        try (SFTPClient sftp = ftpDsl.getSftpClient()) {
            ftpDsl
                .waitForFileOnSftp(sftp, letterId)
                .validate(sftp, letterId, numberOfDocuments);
        }
    }
}
