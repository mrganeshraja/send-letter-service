package uk.gov.hmcts.reform.sendletter.services;

import org.junit.Before;
import org.junit.Test;
import uk.gov.hmcts.reform.pdf.generator.HTMLToPDFConverter;
import uk.gov.hmcts.reform.sendletter.exception.DuplexException;
import uk.gov.hmcts.reform.sendletter.services.pdf.DuplexPreparator;
import uk.gov.hmcts.reform.sendletter.services.pdf.PdfCreator;

import java.util.List;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class PdfCreatorTest {

    private PdfCreator pdfCreator;

    @Before
    public void setUp() {
        this.pdfCreator = new PdfCreator(new DuplexPreparator(), new HTMLToPDFConverter()::convert);
    }

    @Test
    public void should_handle_base64_encoded_pdfs() throws Exception {
        // given

        List<byte[]> pdfs = asList(
            toByteArray(getResource("pdfs/test1.pdf")),
            toByteArray(getResource("pdfs/test2.pdf"))
        );

        // when
        byte[] pdfContent = pdfCreator.createFromBase64Pdfs(pdfs);

        // then
        assertThat(pdfContent).isNotNull();
        // and no exception is thrown
    }

    @Test
    public void should_throw_an_exception_if_bytes_do_not_represent_pdf() {
        // given
        List<byte[]> pdfs = asList(
            "clearly not a pdf".getBytes()
        );

        // when
        Throwable exc = catchThrowable(() -> pdfCreator.createFromBase64Pdfs(pdfs));

        // then
        assertThat(exc)
            .isNotNull()
            .isInstanceOf(DuplexException.class);
    }
}
