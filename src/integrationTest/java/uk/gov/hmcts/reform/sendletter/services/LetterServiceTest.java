package uk.gov.hmcts.reform.sendletter.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.pdf.generator.HTMLToPDFConverter;
import uk.gov.hmcts.reform.sendletter.PdfHelper;
import uk.gov.hmcts.reform.sendletter.SampleData;
import uk.gov.hmcts.reform.sendletter.config.SpyOnJpaConfig;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.model.in.LetterRequest;
import uk.gov.hmcts.reform.sendletter.services.pdf.DuplexPreparator;
import uk.gov.hmcts.reform.sendletter.services.pdf.PdfCreator;
import uk.gov.hmcts.reform.sendletter.services.zip.Zipper;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static uk.gov.hmcts.reform.sendletter.entity.LetterStatus.Created;
import static uk.gov.hmcts.reform.sendletter.entity.LetterStatus.Uploaded;

@RunWith(SpringRunner.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@ImportAutoConfiguration(SpyOnJpaConfig.class)
public class LetterServiceTest {

    private static final String SERVICE_NAME = "bulkprint";

    private LetterService service;

    @Autowired
    private LetterRepository letterRepository;

    @Before
    public void setUp() {
        service = new LetterService(
            new PdfCreator(new DuplexPreparator(), new HTMLToPDFConverter()::convert),
            letterRepository,
            new Zipper(),
            new ObjectMapper(),
            false,
            null
        );
    }

    @After
    public void tearDown() {
        reset(letterRepository);
    }

    @Test
    public void generates_and_saves_zipped_pdf() throws IOException {
        UUID id = service.send(SampleData.letterRequest(), SERVICE_NAME);

        Letter result = letterRepository.findById(id).get();
        PdfHelper.validateZippedPdf(result.getFileContent());
    }

    @Test
    public void returns_same_id_on_resubmit() throws IOException {
        // given
        LetterRequest sampleRequest = SampleData.letterRequest();
        UUID id1 = service.send(sampleRequest, SERVICE_NAME);
        Letter letter = letterRepository.findById(id1).get();

        // and
        assertThat(letter.getStatus()).isEqualByComparingTo(Created);

        // when
        UUID id2 = service.send(sampleRequest, SERVICE_NAME);

        // then
        assertThat(id1).isEqualByComparingTo(id2);

        // and
        verify(letterRepository).save(any(Letter.class));
    }

    @Test
    public void saves_an_new_letter_if_previous_one_has_been_sent_to_print() throws IOException {
        // given
        LetterRequest sampleRequest = SampleData.letterRequest();
        UUID id1 = service.send(sampleRequest, SERVICE_NAME);
        Letter letter = letterRepository.findById(id1).get();

        // and
        assertThat(letter.getStatus()).isEqualByComparingTo(Created);

        // when
        letter.setStatus(Uploaded);
        letterRepository.saveAndFlush(letter);
        UUID id2 = service.send(sampleRequest, SERVICE_NAME);

        // then
        assertThat(id1).isNotEqualByComparingTo(id2);

        // and
        verify(letterRepository, times(2)).save(any(Letter.class));
    }

    @Test
    public void should_not_allow_null_service_name() {
        assertThatThrownBy(() -> service.send(SampleData.letterRequest(), null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void should_not_allow_empty_service_name() {
        assertThatThrownBy(() -> service.send(SampleData.letterRequest(), ""))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void handles_null_timestamps() {
        assertThat(LetterService.toDateTime(null)).isNull();
    }
}
