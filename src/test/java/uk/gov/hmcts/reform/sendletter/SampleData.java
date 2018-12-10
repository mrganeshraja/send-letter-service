package uk.gov.hmcts.reform.sendletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import uk.gov.hmcts.reform.sendletter.model.LetterPrintStatus;
import uk.gov.hmcts.reform.sendletter.model.ParsedReport;
import uk.gov.hmcts.reform.sendletter.model.in.Document;
import uk.gov.hmcts.reform.sendletter.model.in.LetterRequest;
import uk.gov.hmcts.reform.sendletter.model.in.LetterWithPdfsRequest;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static com.google.common.io.Resources.getResource;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public final class SampleData {

    public static LetterRequest letterRequest() {
        try {
            return new LetterRequest(
                singletonList(
                    new Document(
                        Resources.toString(getResource("template.html"), UTF_8),
                        ImmutableMap.of(
                            "name", "John",
                            "reference", UUID.randomUUID()
                        )
                    )
                ),
                "someType",
                Maps.newHashMap()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static LetterWithPdfsRequest letterWithPdfsRequest() {
        return new LetterWithPdfsRequest(
            singletonList(
                Base64.getEncoder().encode("hello world".getBytes())
            ),
            "someType",
            Maps.newHashMap()
        );
    }

    public static uk.gov.hmcts.reform.sendletter.entity.Letter letterEntity(String service) {
        try {
            return new uk.gov.hmcts.reform.sendletter.entity.Letter(
                UUID.randomUUID(),
                "messageId",
                service,
                new ObjectMapper().readTree("{}"),
                "a type",
                new byte[1],
                false,
                Timestamp.valueOf(LocalDateTime.now())
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static ParsedReport parsedReport(String filename, List<UUID> letterIds, boolean allParsed) {
        return new ParsedReport(
            filename,
            letterIds
                .stream()
                .map(id -> new LetterPrintStatus(id, ZonedDateTime.now()))
                .collect(toList()),
            allParsed
        );
    }

    public static ParsedReport parsedReport(String filename, boolean allParsed) {
        return parsedReport(filename, Arrays.asList(UUID.randomUUID(), UUID.randomUUID()), allParsed);
    }

    private SampleData() {
    }
}
