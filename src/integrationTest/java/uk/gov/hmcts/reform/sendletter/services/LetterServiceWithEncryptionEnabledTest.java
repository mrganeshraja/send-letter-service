package uk.gov.hmcts.reform.sendletter.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.pdf.generator.HTMLToPDFConverter;
import uk.gov.hmcts.reform.sendletter.SampleData;
import uk.gov.hmcts.reform.sendletter.config.SpyOnJpaConfig;
import uk.gov.hmcts.reform.sendletter.entity.Letter;
import uk.gov.hmcts.reform.sendletter.entity.LetterRepository;
import uk.gov.hmcts.reform.sendletter.model.in.LetterRequest;
import uk.gov.hmcts.reform.sendletter.services.encryption.PgpDecryptionHelper;
import uk.gov.hmcts.reform.sendletter.services.pdf.DuplexPreparator;
import uk.gov.hmcts.reform.sendletter.services.pdf.PdfCreator;
import uk.gov.hmcts.reform.sendletter.services.zip.Zipper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;

@RunWith(SpringRunner.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest
@ImportAutoConfiguration(SpyOnJpaConfig.class)
public class LetterServiceWithEncryptionEnabledTest {

    private static final String SERVICE_NAME = "a_service";

    @Value("${encryption.publicKey}")
    private String encryptionPublicKey;

    @Autowired
    private LetterRepository letterRepository;

    @After
    public void tearDown() {
        reset(letterRepository);
    }

    @Test
    public void generates_and_saves_encrypted_zip_when_encryption_is_enabled() throws Exception {
        LetterRequest letterRequest = SampleData.letterRequest();

        PdfCreator pdfCreator = new PdfCreator(new DuplexPreparator(), new HTMLToPDFConverter()::convert);

        LetterService service = new LetterService(
            pdfCreator,
            letterRepository,
            new Zipper(),
            new ObjectMapper(),
            true,
            encryptionPublicKey
        );

        UUID id = service.send(letterRequest, SERVICE_NAME);

        Letter letterInDb = letterRepository.findOne(id);

        byte[] encryptedZip = letterInDb.getFileContent();

        PgpDecryptionHelper.DecryptedFile decryptedZip = PgpDecryptionHelper.decryptFile(
            encryptedZip,
            getClass().getResourceAsStream("/encryption/privatekey.asc"),
            "Password1".toCharArray()
        );
        //then
        //We don't have the original zip so just verifying if we could decrypt the encrypted file.
        assertThat(decryptedZip.content).isNotEmpty();
    }
}
