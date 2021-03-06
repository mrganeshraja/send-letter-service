package uk.gov.hmcts.reform.sendletter.tasks;

import net.javacrumbs.shedlock.core.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.entity.LetterStatus;
import uk.gov.hmcts.reform.sendletter.logging.AppInsights;
import uk.gov.hmcts.reform.sendletter.services.ftp.IFtpAvailabilityChecker;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Stream;

/**
 * Task to run report on unprinted letters and report them to AppInsights.
 */
@Component
@ConditionalOnProperty(value = "scheduling.enabled", matchIfMissing = true)
public class StaleLettersTask {
    private static final Logger logger = LoggerFactory.getLogger(StaleLettersTask.class);
    private static final String TASK_NAME = "StaleLetters";

    private final LetterRepository repo;
    private final AppInsights insights;
    private final LocalTime staleCutOffTime;

    public StaleLettersTask(
        LetterRepository repo,
        AppInsights insights,
        IFtpAvailabilityChecker checker
    ) {
        this.repo = repo;
        this.insights = insights;
        this.staleCutOffTime = checker.getDowntimeStart();
    }

    @Transactional
    @SchedulerLock(name = TASK_NAME)
    @Scheduled(cron = "${tasks.stale-letters-report}")
    public void run() {
        Timestamp staleCutOff = Timestamp.valueOf(
            LocalDateTime.now()
                .minusDays(1)
                .with(staleCutOffTime)
        );

        logger.info("Started '{}' task with cut-off of {}", TASK_NAME, staleCutOff);

        try (Stream<Letter> letters = repo.findByStatusAndSentToPrintAtBefore(LetterStatus.Uploaded, staleCutOff)) {
            long count = letters.peek(insights::trackStaleLetter).count();
            logger.info("Completed '{}' task. Letters found: {}", TASK_NAME, count);
        }
    }
}
