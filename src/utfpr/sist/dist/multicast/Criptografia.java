package utfpr.sist.dist.multicast;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import utfpr.sist.dist.util.Util;

public class Criptografia {
	
	private Criptografia() {
	}

    public static Map<String,Object> getRSAKeys() 
    		throws NoSuchAlgorithmException
    {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(512);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        Map<String, Object> keys = new HashMap<String,Object>();
        keys.put(Util.PRIVATE_KEY, privateKey);
        keys.put(Util.PUBLIC_KEY, publicKey);
        return keys;
    }

    public static String encryptMessage(String plainText, PrivateKey privateKey) 
    		throws NoSuchAlgorithmException, NoSuchPaddingException, 
    		InvalidKeyException, IllegalBlockSizeException, BadPaddingException
    {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, privateKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes()));
    }
    
    public static String decryptMessage(String encryptedText, PublicKey publicKey) 
    		throws NoSuchAlgorithmException, NoSuchPaddingException, 
    				InvalidKeyException, IllegalBlockSizeException, BadPaddingException
    {
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedText)));
    }
}
