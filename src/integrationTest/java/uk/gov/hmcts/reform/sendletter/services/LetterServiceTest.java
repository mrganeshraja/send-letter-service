package uk.gov.hmcts.reform.sendletter.services;

import org.apache.pdfbox.preflight.PreflightDocument;
import org.apache.pdfbox.preflight.parser.PreflightParser;
import org.apache.pdfbox.preflight.utils.ByteArrayDataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sendletter.SampleData;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import javax.activation.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@RunWith(SpringRunner.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
public class LetterServiceTest {

    private LetterService service;

    @Autowired
    private LetterRepository letterRepository;

    @Before
    public void setUp() {
        service = new LetterService(letterRepository);
    }

    @Test
    public void generates_and_saves_pdf() throws IOException {
        UUID id = service.send(SampleData.letter(), "a_service");
        Letter result = letterRepository.findOne(id);
        DataSource dataSource = new ByteArrayDataSource(new ByteArrayInputStream(result.getPdf()));
        PreflightParser pdfParser = new PreflightParser(dataSource);
        pdfParser.parse();
        PreflightDocument document = pdfParser.getPreflightDocument();
        // This will throw an exception if the file format is invalid,
        // but ignores more minor errors such as missing metadata.
        document.validate();
    }

    @Test
    public void should_not_allow_null_service_name() {
        assertThatThrownBy(() -> service.send(SampleData.letter(), null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void should_not_allow_empty_service_name() {
        assertThatThrownBy(() -> service.send(SampleData.letter(), ""))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void handles_null_timestamps() {
        assertThat(LetterService.toDateTime(null)).isNull();
    }
}