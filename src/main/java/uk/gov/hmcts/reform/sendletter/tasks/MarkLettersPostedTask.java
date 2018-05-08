package uk.gov.hmcts.reform.sendletter.tasks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.entity.LetterStatus;
import uk.gov.hmcts.reform.sendletter.exception.FtpException;
import uk.gov.hmcts.reform.sendletter.logging.AppInsights;
import uk.gov.hmcts.reform.sendletter.model.LetterPrintStatus;
import uk.gov.hmcts.reform.sendletter.services.ReportParser;
import uk.gov.hmcts.reform.sendletter.services.ftp.FtpClient;
import uk.gov.hmcts.reform.sendletter.services.ftp.IFtpAvailabilityChecker;

import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.Optional;

import static uk.gov.hmcts.reform.sendletter.tasks.Task.MarkLettersPosted;

/**
 * Fetches reports from Xerox SFTP concerning posted
 * letters and sets posted letters as Posted in the database.
 */
@Component
public class MarkLettersPostedTask {

    private final LetterRepository repo;
    private final FtpClient ftpClient;
    private final IFtpAvailabilityChecker ftpAvailabilityChecker;
    private final ReportParser parser;
    private final AppInsights insights;

    private static final Logger logger = LoggerFactory.getLogger(MarkLettersPostedTask.class);

    public MarkLettersPostedTask(
        LetterRepository repo,
        FtpClient ftp,
        IFtpAvailabilityChecker checker,
        ReportParser parser,
        AppInsights insights
    ) {
        this.repo = repo;
        this.ftpClient = ftp;
        this.ftpAvailabilityChecker = checker;
        this.parser = parser;
        this.insights = insights;
    }

    public void run(LocalTime now) {
        if (!ftpAvailabilityChecker.isFtpAvailable(now)) {
            logger.info("Not processing '{}' task due to FTP downtime window", MarkLettersPosted);
            return;
        }

        logger.info("Started '{}' task", MarkLettersPosted);
        try {
            ftpClient
                .downloadReports()
                .stream()
                .map(parser::parse)
                .forEach(parsedReport -> {
                    insights.trackPrintReportReceived(parsedReport);
                    parsedReport.statuses.forEach(this::markAsPosted);

                    if (parsedReport.allRowsParsed) {
                        logger.info("Report {} successfully parsed, deleting", parsedReport.path);
                        ftpClient.deleteReport(parsedReport.path);
                    } else {
                        logger.warn("Report {} contained invalid rows, file not removed.", parsedReport.path);
                    }
                });

            logger.info("Completed '{}' task", MarkLettersPosted);
        } catch (FtpException f) {
            logger.warn("Error fetching Xerox reports", f);
        }
    }

    private void markAsPosted(LetterPrintStatus letterPrintStatus) {
        Optional<Letter> optional = repo.findById(letterPrintStatus.id);
        if (optional.isPresent()) {
            Letter letter = optional.get();
            if (letter.getStatus() == LetterStatus.Uploaded) {
                letter.setPrintedAt(Timestamp.from(letterPrintStatus.printedAt.toInstant()));
                letter.setStatus(LetterStatus.Posted);
                repo.save(letter);
                logger.info("Marked letter {} as posted", letter.getId());
            } else {
                logger.warn(
                    "Failed to mark letter as posted {} - unexpected status: {}",
                    letter.getId(),
                    letter.getStatus()
                );
            }
        } else {
            logger.warn(
                "Failed to mark letter {} as posted - unknown letter",
                letterPrintStatus.id
            );
        }
    }
}
