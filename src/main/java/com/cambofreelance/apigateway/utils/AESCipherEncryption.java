package com.cambofreelance.apigateway.utils;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AESCipherEncryption {
    @Value("${aes-cipher.gcm.key}")
    private String aesKey;

    @Value("${aes-cipher.gcm.algorithm}")
    private String aesAlgorithm;

    @Value("${aes-cipher.gcm.tag-size}")
    private String tagSize;

    public String encrypt(String plainText, String givenIv) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, UnsupportedEncodingException, IllegalBlockSizeException, BadPaddingException {
        byte[] aesKeyByte = Base64.decodeBase64(aesKey);
        byte[] ivLength = Base64.decodeBase64(givenIv);
        int gcmTagSize = Integer.parseInt(tagSize);

        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final byte[] digestOfPassword = md.digest(aesKeyByte);
        final SecretKey key = new SecretKeySpec(digestOfPassword, "AES");

        final Cipher cipher = Cipher.getInstance(aesAlgorithm);
        final AlgorithmParameterSpec iv = generateAesAlgorithm(aesAlgorithm, ivLength, gcmTagSize);

        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        final byte[] plainTextBytes = plainText.getBytes("utf-8");
        final byte[] encodeTextBytes = cipher.doFinal(plainTextBytes);

        return new Base64().encodeToString(encodeTextBytes);
    }

    public String decrypt(String cipherText, String givenIv) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        byte[] aesKeyByte = Base64.decodeBase64(aesKey);
        byte[] ivLength = Base64.decodeBase64(givenIv);
        int gcmTagSize = Integer.parseInt(tagSize);

        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final byte[] digestOfPassword = md.digest(aesKeyByte);
        final SecretKey key = new SecretKeySpec(digestOfPassword, "AES");

        final Cipher cipher = Cipher.getInstance(aesAlgorithm);
        final AlgorithmParameterSpec iv = generateAesAlgorithm(aesAlgorithm, ivLength, gcmTagSize);

        cipher.init(Cipher.DECRYPT_MODE, key, iv);

        final byte[] plainTextBytes = Base64.decodeBase64(cipherText);
        final byte[] encodeTextBytes = cipher.doFinal(plainTextBytes);

        return new String(encodeTextBytes);
    }


    private static AlgorithmParameterSpec generateAesAlgorithm(String algorithm, byte[] ivLength, int gcmKeySize) {
        if ("AES/GCM/NoPadding".equals(algorithm)) {
            GCMParameterSpec gcm = new GCMParameterSpec(gcmKeySize, ivLength);
            return gcm;
        }

        // APPLY_DEFAULT_CBC
        return new IvParameterSpec(ivLength);
    }

    public static String generateIv() {
        byte[] iv = new byte[12];
        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(iv);

        String base64IV = Base64.encodeBase64String(iv);
        return base64IV;
    }
}
