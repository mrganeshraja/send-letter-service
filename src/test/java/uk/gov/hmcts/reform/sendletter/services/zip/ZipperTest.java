package uk.gov.hmcts.reform.sendletter.services.zip;

import org.junit.Test;
import uk.gov.hmcts.reform.sendletter.model.PdfDoc;

import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;

import static com.google.common.io.Resources.getResource;
import static com.google.common.io.Resources.toByteArray;
import static org.assertj.core.api.Assertions.assertThat;

public class ZipperTest {

    @Test
    public void should_zip_file() throws Exception {
        byte[] fileContent = toByteArray(getResource("hello.pdf"));
        byte[] expectedZipFileContent = toByteArray(getResource("hello.zip"));

        byte[] result = new Zipper().zip(
            new PdfDoc("hello.pdf", fileContent)
        );

        assertThat(result).isNotNull();
        assertThat(asZip(result)).hasSameContentAs(asZip(expectedZipFileContent));
    }

    private ZipInputStream asZip(byte[] bytes) {
        return new ZipInputStream(new ByteArrayInputStream(bytes));
    }
}
