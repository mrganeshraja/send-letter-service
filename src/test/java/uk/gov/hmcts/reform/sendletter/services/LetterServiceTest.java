package uk.gov.hmcts.reform.sendletter.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import uk.gov.hmcts.reform.sendletter.SampleData;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.model.PdfDoc;
import uk.gov.hmcts.reform.sendletter.model.in.LetterRequest;
import uk.gov.hmcts.reform.sendletter.model.in.LetterWithPdfsRequest;
import uk.gov.hmcts.reform.sendletter.services.encryption.UnableToLoadPgpPublicKeyException;
import uk.gov.hmcts.reform.sendletter.services.pdf.PdfCreator;
import uk.gov.hmcts.reform.sendletter.services.zip.Zipper;

import java.io.IOException;
import java.util.Optional;

import static com.google.common.io.Resources.getResource;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LetterServiceTest {

    @Mock PdfCreator pdfCreator;
    @Mock LetterRepository letterRepository;
    @Mock Zipper zipper;
    @Mock ObjectMapper objectMapper;

    private LetterService service;

    @Before
    public void setUp() {
        thereAreNoDuplicates();
    }

    @Test
    public void should_generate_final_pdf_from_template_when_old_model_is_passed() throws Exception {
        // given
        createLetterService(false, null);

        LetterRequest letter = SampleData.letterRequest();

        // when
        service.send(letter, "some_service");

        // then
        verify(pdfCreator).createFromTemplates(letter.documents);
    }

    @Test
    public void should_generate_final_pdf_from_embedded_pdfs_when_new_model_is_passed() throws Exception {
        // given
        createLetterService(false, null);

        LetterWithPdfsRequest letter = SampleData.letterWithPdfsRequest();

        // when
        service.send(letter, "some_service");

        // then
        verify(pdfCreator).createFromBase64Pdfs(letter.documents);
    }

    @Test
    public void should_generate_final_pdf_from_template_when_old_model_is_passed_and_encryption_enabled()
        throws Exception {
        // given
        createLetterService(true, new String(loadPublicKey()));

        LetterRequest letter = SampleData.letterRequest();

        byte[] inputZipFile = Resources.toByteArray(getResource("unencrypted.zip"));

        when(zipper.zip(any(PdfDoc.class))).thenReturn(inputZipFile);

        // when
        service.send(letter, "some_service");

        // then
        verify(pdfCreator).createFromTemplates(letter.documents);
        verify(zipper).zip(any(PdfDoc.class));
    }

    @Test
    public void should_generate_final_pdf_from_embedded_pdfs_when_new_model_is_passed_and_encryption_enabled()
        throws Exception {
        // given
        createLetterService(true, new String(loadPublicKey()));

        LetterWithPdfsRequest letter = SampleData.letterWithPdfsRequest();

        byte[] inputZipFile = Resources.toByteArray(getResource("unencrypted.zip"));

        when(zipper.zip(any(PdfDoc.class))).thenReturn(inputZipFile);

        // when
        service.send(letter, "some_service");

        // then
        verify(pdfCreator).createFromBase64Pdfs(letter.documents);
        verify(zipper).zip(any(PdfDoc.class));
    }

    @Test
    public void should_throw_unable_to_load_pgp_pub_key_exc_on_init_when_enc_enabled_and_invalid_pub_key_is_passed() {
        assertThatThrownBy(() -> createLetterService(true, "This is not a proper pgp public key"))
            .isInstanceOf(UnableToLoadPgpPublicKeyException.class);
    }

    @Test
    public void should_throw_assertion_error_on_service_init_when_encryption_enabled_and_public_key_is_null() {
        assertThatThrownBy(() -> createLetterService(true, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("encryptionPublicKey is null");
    }

    private void thereAreNoDuplicates() {
        given(letterRepository.findByMessageIdAndStatusOrderByCreatedAtDesc(any(), any()))
            .willReturn(Optional.empty());
    }

    private void createLetterService(Boolean isEncryptionEnabled, String encryptionKey) {
        this.service = new LetterService(
            pdfCreator,
            letterRepository,
            zipper,
            objectMapper,
            isEncryptionEnabled,
            encryptionKey
        );
    }

    private byte[] loadPublicKey() throws IOException {
        return Resources.toByteArray(getResource("encryption/pubkey.asc"));
    }
}
