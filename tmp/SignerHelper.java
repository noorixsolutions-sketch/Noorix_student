import com.google.crypto.tink.subtle.Ed25519Sign;
import java.util.Base64;

public class SignerHelper {
    public static void main(String[] args) throws Exception {
        byte[] seed = "NoorixCurriculumPublisherKey2025".getBytes("UTF-8");
        Ed25519Sign.KeyPair kp = Ed25519Sign.KeyPair.newKeyPairFromSeed(seed);
        Ed25519Sign signer = new Ed25519Sign(kp.getPrivateKey());

        String mathHash = "05bc022f52109f283911abb29022dcde97f00cc94fdbd72544453f188fb40d78";
        String physHash = "aefdf60e7db7e88f97ae022422c487ad1496c4e86ff9a5c4b865768cfe8de397";
        String chemHash = "f7ef6cd1cec05981d25cf09ad3d52da529f81ada85b062a720fdcb6fbd793ca1";

        System.out.println("PUB_B64=" + Base64.getEncoder().encodeToString(kp.getPublicKey()));
        System.out.println("MATH_SIG=" + Base64.getEncoder().encodeToString(signer.sign(mathHash.getBytes("UTF-8"))));
        System.out.println("PHYS_SIG=" + Base64.getEncoder().encodeToString(signer.sign(physHash.getBytes("UTF-8"))));
        System.out.println("CHEM_SIG=" + Base64.getEncoder().encodeToString(signer.sign(chemHash.getBytes("UTF-8"))));
    }
}
