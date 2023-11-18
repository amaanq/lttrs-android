package rs.ltt.android.push;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.base.CharMatcher;
import com.google.common.io.BaseEncoding;
import java.security.GeneralSecurityException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class WebPushMessageEncryptionTest {

    @Test
    public void rfc8291TestVectors() throws GeneralSecurityException {
        final var body =
                """
                DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27ml
                mlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPT
                pK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN""";
        final var authenticationSecret = BaseEncoding.base64Url().decode("BTBZMqHH6r4Tts7J_aSIgg");
        final var privateKey = "q1dXpw3UpT5VOmu_cf_v6ih07Aems3njxI-JWgLcM94";
        final var publicKey =
                CharMatcher.whitespace()
                        .removeFrom(
                                """
                BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcx
                aOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4""");
        final var ciphertext =
                BaseEncoding.base64Url().decode(CharMatcher.whitespace().removeFrom(body));

        final var plaintext =
                WebPushMessageEncryption.decrypt(
                        ciphertext,
                        BaseEncoding.base64Url().decode(publicKey),
                        BaseEncoding.base64Url().decode(privateKey),
                        authenticationSecret);
        final var plaintextAsString = new String(plaintext);
        Assert.assertEquals("When I grow up, I want to be a watermelon", plaintextAsString);
    }

    @Test
    public void testGenerateKeyMaterial() throws GeneralSecurityException {
        final var keyMaterial = WebPushMessageEncryption.generateKeyMaterial();
        Assert.assertNotNull(keyMaterial.encodedPublicKey());
        Assert.assertNotNull(keyMaterial.encodedAuthenticationSecret());
    }
}
