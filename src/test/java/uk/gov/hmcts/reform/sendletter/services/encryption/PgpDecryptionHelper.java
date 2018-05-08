package uk.gov.hmcts.reform.sendletter.services.encryption;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPOnePassSignatureList;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.PBESecretKeyDecryptor;
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyDataDecryptorFactory;
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.Iterator;

public final class PgpDecryptionHelper {
    // Prevent instantiation.
    private PgpDecryptionHelper() {
    }

    /**
     * decrypt the passed in message stream.
     */
    @SuppressWarnings("unchecked")
    public static DecryptedFile decryptFile(
        byte[] in,
        InputStream keyIn,
        char... passwd
    ) throws IOException, PGPException {

        Security.addProvider(new BouncyCastleProvider());

        PGPObjectFactory objectFactory = new PGPObjectFactory(in, new JcaKeyFingerprintCalculator());
        PGPEncryptedDataList enc;

        Object o = objectFactory.nextObject();

        // the first object might be a PGP marker packet.
        if (o instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) o;
        } else {
            enc = (PGPEncryptedDataList) objectFactory.nextObject();
        }

        //
        // find the secret key
        //
        Iterator<PGPPublicKeyEncryptedData> it = enc.getEncryptedDataObjects();
        PGPPrivateKey pgpPrivateKey = null;
        PGPPublicKeyEncryptedData pbe = null;

        while (pgpPrivateKey == null && it.hasNext()) {
            pbe = it.next();

            pgpPrivateKey = findPrivateKey(keyIn, pbe.getKeyID(), passwd);
        }

        if (pgpPrivateKey == null) {
            throw new IllegalArgumentException("Secret key for message not found.");
        }

        InputStream clear = pbe.getDataStream(new BcPublicKeyDataDecryptorFactory(pgpPrivateKey));

        PGPObjectFactory plainFact = new PGPObjectFactory(clear, new JcaKeyFingerprintCalculator());

        Object message = plainFact.nextObject();

        if (message instanceof PGPCompressedData) {
            PGPCompressedData compressedData = (PGPCompressedData) message;
            PGPObjectFactory pgpFact =
                new PGPObjectFactory(
                    compressedData.getDataStream(),
                    new JcaKeyFingerprintCalculator()
                );

            message = pgpFact.nextObject();
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        if (message instanceof PGPLiteralData) {
            PGPLiteralData ld = (PGPLiteralData) message;

            InputStream unc = ld.getInputStream();
            int ch;

            while ((ch = unc.read()) >= 0) {
                byteArrayOutputStream.write(ch);
            }

            if (pbe.isIntegrityProtected() && !pbe.verify()) {
                throw new PGPException("Message failed integrity check");
            }

            return new DecryptedFile(ld.getFileName(), byteArrayOutputStream.toByteArray());
        } else if (message instanceof PGPOnePassSignatureList) {
            throw new PGPException("Encrypted message contains a signed message - not literal data.");
        } else {
            throw new PGPException("Message is not a simple encrypted file - type unknown.");
        }
    }

    private static PGPPrivateKey findPrivateKey(InputStream keyIn, long keyId, char... pass)
        throws IOException, PGPException {
        PGPSecretKeyRingCollection pgpSec =
            new PGPSecretKeyRingCollection(
                PGPUtil.getDecoderStream(keyIn),
                new JcaKeyFingerprintCalculator()
            );
        return findPrivateKey(pgpSec.getSecretKey(keyId), pass);
    }

    private static PGPPrivateKey findPrivateKey(PGPSecretKey pgpSecKey, char... pass)
        throws PGPException {
        if (pgpSecKey == null) {
            return null;
        }

        PBESecretKeyDecryptor decryptor =
            new BcPBESecretKeyDecryptorBuilder(
                new BcPGPDigestCalculatorProvider()
            ).build(pass);
        return pgpSecKey.extractPrivateKey(decryptor);
    }

    public static class DecryptedFile {
        public final String filename;
        public final byte[] content;

        public DecryptedFile(String filename, byte[] content) {
            this.filename = filename;
            this.content = content;
        }
    }
}
