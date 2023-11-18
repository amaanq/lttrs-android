package rs.ltt.android.push;

import com.google.common.io.BaseEncoding;
import com.google.crypto.tink.HybridDecrypt;
import com.google.crypto.tink.apps.webpush.WebPushHybridDecrypt;
import com.google.crypto.tink.subtle.EllipticCurves;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

public class WebPushMessageEncryption {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static byte[] decrypt(final byte[] ciphertext, final KeyMaterial keyMaterial)
            throws GeneralSecurityException {
        return decrypt(
                ciphertext,
                keyMaterial.publicKey,
                keyMaterial.privateKey,
                keyMaterial.authenticationSecret);
    }

    public static byte[] decrypt(
            final byte[] ciphertext,
            final byte[] publicKey,
            final byte[] privateKey,
            final byte[] authenticationSecret)
            throws GeneralSecurityException {
        final HybridDecrypt hybridDecrypt =
                new WebPushHybridDecrypt.Builder()
                        .withAuthSecret(authenticationSecret)
                        .withRecipientPublicKey(publicKey)
                        .withRecipientPrivateKey(privateKey)
                        .build();
        return hybridDecrypt.decrypt(ciphertext, null);
    }

    public static KeyMaterial generateKeyMaterial() throws GeneralSecurityException {
        final var keyPair = EllipticCurves.generateKeyPair(EllipticCurves.CurveType.NIST_P256);
        final byte[] privateKey = keyPair.getPrivate().getEncoded();
        final byte[] publicKey = keyPair.getPublic().getEncoded();
        final byte[] authenticationSecret = SECURE_RANDOM.generateSeed(12);
        return new KeyMaterial(publicKey, privateKey, authenticationSecret);
    }

    public static class KeyMaterial {
        public final byte[] publicKey;
        public final byte[] privateKey;
        public final byte[] authenticationSecret;

        public KeyMaterial(byte[] publicKey, byte[] privateKey, byte[] authenticationSecret) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
            this.authenticationSecret = authenticationSecret;
        }

        public String encodedPublicKey() {
            return BaseEncoding.base64Url().encode(this.publicKey);
        }

        public String encodedAuthenticationSecret() {
            return BaseEncoding.base64Url().encode(this.authenticationSecret);
        }
    }
}
