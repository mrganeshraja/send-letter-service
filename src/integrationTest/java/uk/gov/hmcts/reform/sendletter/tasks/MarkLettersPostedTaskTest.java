package uk.gov.hmcts.reform.sendletter.tasks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sendletter.SampleData;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.entity.LetterStatus;
import uk.gov.hmcts.reform.sendletter.helper.FtpHelper;
import uk.gov.hmcts.reform.sendletter.logging.AppInsights;
import uk.gov.hmcts.reform.sendletter.model.ParsedReport;
import uk.gov.hmcts.reform.sendletter.services.LocalSftpServer;
import uk.gov.hmcts.reform.sendletter.services.ReportParser;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpAvailabilityChecker;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpClient;
import uk.gov.hmcts.reform.sendletter.util.XeroxReportWriter;

import java.time.LocalTime;
import java.util.stream.Stream;
import javax.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
public class MarkLettersPostedTaskTest {

    @Autowired
    LetterRepository repository;

    @Autowired
    private EntityManager entityManager;

    ReportParser parser = new ReportParser();
    FtpAvailabilityChecker checker = mock(FtpAvailabilityChecker.class);
    AppInsights insights = mock(AppInsights.class);

    @Test
    public void marks_uploaded_letters_as_posted() throws Exception {
        // Create a letter in the Uploaded state.
        Letter letter = SampleData.letterEntity("bulkprint");
        letter.setStatus(LetterStatus.Uploaded);
        repository.saveAndFlush(letter);

        when(checker.isFtpAvailable(any(LocalTime.class))).thenReturn(true);
        try (LocalSftpServer server = LocalSftpServer.create()) {
            FtpClient client = FtpHelper.getSuccessfulClient(LocalSftpServer.port);
            MarkLettersPostedTask task = new MarkLettersPostedTask(repository, client, checker, parser, insights);

            // Prepare the response CSV from Xerox and run the task.
            XeroxReportWriter.writeReport(Stream.of(letter.getId()), server.reportFolder);
            task.run();
        }

        // Check that the letter has moved to the Posted state.
        entityManager.flush();
        letter = repository.findById(letter.getId()).get();
        assertThat(letter.getStatus()).isEqualTo(LetterStatus.Posted);
        // Check that printed at date has been set.
        assertThat(letter.getPrintedAt()).isNotNull();

        verify(insights).trackPrintReportReceived(any(ParsedReport.class));
    }
}
