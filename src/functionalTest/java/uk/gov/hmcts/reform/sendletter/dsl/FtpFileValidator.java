package uk.gov.hmcts.reform.sendletter.dsl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.platform.commons.util.StringUtils;

import java.io.IOException;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FtpFileValidator {

    private final Boolean isEncryptionEnabled;

    private final String s2sName;

    private final String letterId;

    FtpFileValidator(ConfigWrapper config, String letterId) {
        this.isEncryptionEnabled = config.getBoolean("encryption.enabled");
        this.s2sName = config.getString("s2s-name");
        this.letterId = letterId;
    }

    FtpFileValidator assertFileNameMatch(String actualFileName, Supplier<String> fileNamePattern) {
        if (StringUtils.isNotBlank(actualFileName)) {
            assertThat(actualFileName).matches(fileNamePattern.get());
        }

        return this;
    }

    FtpFileValidator assertNumberOfPages(byte[] pdf, int expectedNumberOfDocuments) throws IOException {
        if (pdf != null && pdf.length > 0) {
            PDDocument pdfDocument = PDDocument.load(pdf);
            assertThat(pdfDocument.getNumberOfPages()).isEqualTo(expectedNumberOfDocuments);
        }

        return this;
    }

    boolean isEncryptionEnabled() {
        return isEncryptionEnabled;
    }

    String getFtpFileNamePattern() {
        String format = isEncryptionEnabled ? "%s_%s_\\d{14}_%s.pgp" : "%s_%s_\\d{14}_%s.zip";

        return String.format(
            format,
            Pattern.quote("smoketest"),
            Pattern.quote(s2sName.replace("_", "")),
            Pattern.quote(letterId)
        );
    }

    String getPdfFileNamePattern() {
        return String.format(
            "%s_%s_%s.pdf",
            Pattern.quote("smoke_test"),
            Pattern.quote(s2sName.replace("_", "")),
            Pattern.quote(letterId)
        );
    }
}
