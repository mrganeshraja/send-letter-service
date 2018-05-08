package uk.gov.hmcts.reform.sendletter.services.encryption;

import com.google.common.io.Resources;
import org.assertj.core.api.Assertions;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static com.google.common.io.Resources.getResource;
import static org.assertj.core.api.Assertions.assertThat;

public class PgpEncryptionUtilTest {

    @Test
    public void should_encrypt_and_create_pgp_encrypted_zip_file_when_valid_public_key_is_passed()
        throws Exception {
        //Given
        String inputFileName = "unencrypted.zip";

        byte[] inputZipFile = Resources.toByteArray(getResource(inputFileName));

        PGPPublicKey pgpPublicKey = PgpEncryptionUtil.loadPublicKey(loadPublicKey());

        //when
        byte[] pgpEncryptedZip = PgpEncryptionUtil.encryptFile(
            inputZipFile,
            inputFileName,
            pgpPublicKey
        );

        //We are decrypting it using BountyCastle to validate if the decrypted zip is same as input file.
        //Currently this seems to be the only way to validate the file contents.
        PgpDecryptionHelper.DecryptedFile decryptedZip = PgpDecryptionHelper.decryptFile(
            pgpEncryptedZip,
            loadPrivateKey(),
            "Password1".toCharArray()
        );

        //then
        assertThat(inputZipFile).containsExactly(decryptedZip.content);
        assertThat(decryptedZip.filename).isEqualTo(inputFileName);
    }

    @Test
    public void should_throw_custom_exception_when_invalid_pubic_key_is_passed() {
        Throwable exc = Assertions.catchThrowable(
            () -> PgpEncryptionUtil.loadPublicKey("this is not public key".getBytes())
        );

        assertThat(exc)
            .isInstanceOf(UnableToLoadPgpPublicKeyException.class);
    }

    private byte[] loadPublicKey() throws IOException {
        return Resources.toByteArray(getResource("encryption/pubkey.asc"));
    }

    private InputStream loadPrivateKey() {
        return getClass().getResourceAsStream("/encryption/privatekey.asc");
    }
}
