package uk.gov.hmcts.reform.sendletter.tasks;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.pdf.generator.HTMLToPDFConverter;
import uk.gov.hmcts.reform.sendletter.SampleData;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.entity.LetterStatus;
import uk.gov.hmcts.reform.sendletter.exception.FtpException;
import uk.gov.hmcts.reform.sendletter.helper.FtpHelper;
import uk.gov.hmcts.reform.sendletter.logging.AppInsights;
import uk.gov.hmcts.reform.sendletter.services.LetterService;
import uk.gov.hmcts.reform.sendletter.services.LocalSftpServer;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpAvailabilityChecker;
import uk.gov.hmcts.reform.sendletter.services.pdf.DuplexPreparator;
import uk.gov.hmcts.reform.sendletter.services.pdf.PdfCreator;
import uk.gov.hmcts.reform.sendletter.services.zip.Zipper;

import java.io.File;
import java.time.LocalTime;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
public class UploadLettersTaskTest {

    @Autowired
    LetterRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Mock
    private FtpAvailabilityChecker availabilityChecker;

    @Mock
    private AppInsights insights;

    private LetterService letterService;

    @Before
    public void setUp() {
        when(availabilityChecker.isFtpAvailable(any(LocalTime.class))).thenReturn(true);
        this.letterService = new LetterService(
            new PdfCreator(new DuplexPreparator(), new HTMLToPDFConverter()::convert),
            repository,
            new Zipper(),
            new ObjectMapper(),
            false,
            null
        );
    }

    @Test
    public void uploads_file_to_sftp_and_sets_letter_status_to_uploaded() throws Exception {
        UUID id = letterService.send(SampleData.letterRequest(), "bulkprint");
        UploadLettersTask task = new UploadLettersTask(
            repository,
            FtpHelper.getSuccessfulClient(LocalSftpServer.port),
            availabilityChecker,
            insights
        );

        // Invoke the upload job.
        try (LocalSftpServer server = LocalSftpServer.create()) {
            task.run();

            // file should exist in SFTP site.
            File[] files = server.lettersFolder.listFiles();
            assertThat(files.length).isEqualTo(1);

            // Ensure the letter is marked as uploaded in the database.
            // Clear the JPA cache to force a read.
            entityManager.clear();
            Letter l = repository.findById(id).get();
            assertThat(l.getStatus()).isEqualTo(LetterStatus.Uploaded);
            assertThat(l.getSentToPrintAt()).isNotNull();
            assertThat(l.getPrintedAt()).isNull();

            // pdf content should be removed now
            assertThat(l.getFileContent()).isNull();
        }

        verify(insights).trackUploadedLetters(1);
    }

    @Test
    public void should_fail_to_upload_to_sftp_and_stop_from_uploading_any_other_letters() throws Exception {
        // given
        UUID id = letterService.send(SampleData.letterRequest(), "bulkprint");
        // additional letter to verify upload loop broke and zipper was never called again
        letterService.send(SampleData.letterRequest(), "bulkprint");

        // and
        UploadLettersTask task = new UploadLettersTask(
            repository,
            FtpHelper.getFailingClient(LocalSftpServer.port),
            availabilityChecker,
            insights
        );

        // and
        assertThat(repository.findByStatus(LetterStatus.Created).count()).isEqualTo(2);

        // when
        try (LocalSftpServer server = LocalSftpServer.create()) {
            assertThat(catchThrowable(task::run)).isInstanceOf(FtpException.class);

            // then
            // file does not exist in SFTP site.
            assertThat(server.lettersFolder.listFiles()).isEmpty();

            // Clear the JPA cache to force a read.
            entityManager.clear();
            Letter l = repository.findById(id).get();
            assertThat(l.getStatus()).isEqualTo(LetterStatus.Created);
            assertThat(l.getSentToPrintAt()).isNull();
            assertThat(l.getFileContent()).isNotNull();
        }

        verify(insights, never()).trackUploadedLetters(0);
    }

    @Test
    public void should_process_all_letter_batches() throws Exception {
        // Twice the batch size.
        int letterCount = 20;
        IntStream.rangeClosed(1, letterCount).forEach(
            x -> letterService.send(SampleData.letterRequest(), "bulkprint"));

        UploadLettersTask task = new UploadLettersTask(
            repository,
            FtpHelper.getSuccessfulClient(LocalSftpServer.port),
            availabilityChecker,
            insights
        );

        try (LocalSftpServer server = LocalSftpServer.create()) {
            task.run();
        }
        assertThat(repository.findByStatus(LetterStatus.Uploaded).count()).isEqualTo(letterCount);
        verify(insights).trackUploadedLetters(letterCount);
    }
}
