package uk.gov.hmcts.reform.sendletter.tasks;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sendletter.LocalSftpServer;
import uk.gov.hmcts.reform.sendletter.SampleData;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.entity.LetterState;
import uk.gov.hmcts.reform.sendletter.helper.FtpHelper;
import uk.gov.hmcts.reform.sendletter.services.LetterService;

import java.io.File;
import java.util.UUID;
import javax.persistence.EntityManager;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
public class UploadLettersTaskTest {

    @Autowired
    LetterRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void uploads_pdfs_to_sftp_and_saves_letter_as_uploaded() throws Exception {
        LetterService s = new LetterService(repository);
        UUID id = s.send(SampleData.letter(), "service");

        // Invoke the upload job.
        try (LocalSftpServer server = LocalSftpServer.create()) {
            UploadLettersTask task = new UploadLettersTask(repository, FtpHelper.getClient(server.port));

            task.run();

            // PDF should exist in SFTP site.
            File[] files = server.pdfFolder.listFiles();
            assertThat(files.length).isEqualTo(1);

            // Ensure the letter is marked as uploaded in the database.
            // Clear the JPA cache to force a read.
            entityManager.clear();
            Letter l = repository.findById(id).get();
            assertThat(l.getState()).isEqualTo(LetterState.Uploaded);
            assertThat(l.getSentToPrintAt()).isNotNull();
            assertThat(l.getPrintedAt()).isNull();
        }
    }
}