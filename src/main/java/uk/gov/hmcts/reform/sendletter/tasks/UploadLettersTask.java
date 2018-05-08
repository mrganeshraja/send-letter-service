package uk.gov.hmcts.reform.sendletter.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.entity.LetterStatus;
import uk.gov.hmcts.reform.sendletter.logging.AppInsights;
import uk.gov.hmcts.reform.sendletter.services.ftp.FileToSend;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpClient;
import uk.gov.hmcts.reform.sendletter.services.ftp.IFtpAvailabilityChecker;
import uk.gov.hmcts.reform.sendletter.services.util.FinalPackageFileNameHelper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static java.time.LocalDateTime.now;
import static uk.gov.hmcts.reform.sendletter.tasks.Task.UploadLetters;

@Component
public class UploadLettersTask {
    private static final Logger logger = LoggerFactory.getLogger(UploadLettersTask.class);
    public static final String SMOKE_TEST_LETTER_TYPE = "smoke_test";

    private final LetterRepository repo;
    private final FtpClient ftp;
    private final IFtpAvailabilityChecker availabilityChecker;
    private final AppInsights insights;

    public UploadLettersTask(
        LetterRepository repo,
        FtpClient ftp,
        IFtpAvailabilityChecker availabilityChecker,
        AppInsights insights
    ) {
        this.repo = repo;
        this.ftp = ftp;
        this.availabilityChecker = availabilityChecker;
        this.insights = insights;
    }

    public void run() {
        if (!availabilityChecker.isFtpAvailable(now().toLocalTime())) {
            logger.info("Not processing '{}' task due to FTP downtime window", UploadLetters);
            return;
        }

        logger.info("Started '{}' task", UploadLetters);

        // Upload the letters in batches.
        // With each batch we mark them Uploaded so they no longer appear in the query.
        List<Letter> letters;
        int counter = 0;

        try {
            ftp.connect();
            do {
                letters = repo.findFirst10ByStatus(LetterStatus.Created);
                letters
                    .forEach(letter -> {
                        uploadToFtp(letter);
                        markAsUploaded(letter);
                    });
                counter += letters.size();
            } while (!letters.isEmpty());

            if (counter > 0) {
                insights.trackUploadedLetters(counter);
            }
        } finally {
            ftp.disconnect();
        }

        logger.info("Completed '{}' task", UploadLetters);
    }

    private void uploadToFtp(Letter letter) {
        FileToSend file = new FileToSend(
            FinalPackageFileNameHelper.generateName(letter),
            letter.getFileContent()
        );

        logger.debug(
            "Uploading letter id: {}, messageId: {}, file name: {}",
            letter.getId(),
            letter.getMessageId(),
            file.filename
        );

        ftp.upload(file, isSmokeTest(letter));

        logger.info("Successfully uploaded letter {}", letter.getId());
    }

    private void markAsUploaded(Letter letter) {
        letter.setStatus(LetterStatus.Uploaded);
        letter.setSentToPrintAt(Timestamp.from(Instant.now()));

        // remove pdf content, as it's no longer needed
        letter.setFileContent(null);

        repo.saveAndFlush(letter);

        logger.info("Marked letter {} as {}", letter.getId(), letter.getStatus());
    }

    private boolean isSmokeTest(Letter letter) {
        return Objects.equals(letter.getType(), SMOKE_TEST_LETTER_TYPE);
    }
}
