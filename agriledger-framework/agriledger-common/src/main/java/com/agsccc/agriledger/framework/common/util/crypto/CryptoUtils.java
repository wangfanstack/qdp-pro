package com.agsccc.agriledger.framework.common.util.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 基于 JCA 的加密工具类
 * <p>
 * 提供 MD5、SHA-256、HMAC 摘要，以及 AES、RSA 加解密能力，替代原 hutool-crypto 的使用场景。
 * 行为约定：AES 使用 ECB/PKCS5Padding（与 hutool SecureUtil.aes 默认一致）；
 * RSA 使用 ECB/PKCS1Padding 并按密钥长度分块处理，保证长内容的加解密与 Base64 输出格式兼容。
 *
 * @author Sail
 */
public class CryptoUtils {

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static final String AES_ECB_PKCS5 = "AES/ECB/PKCS5Padding";
    private static final String RSA_ECB_PKCS1 = "RSA/ECB/PKCS1Padding";

    // ========== 摘要 ==========

    public static String md5Hex(String data) {
        return md5Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String md5Hex(byte[] data) {
        return digestHex("MD5", data);
    }

    public static String sha256Hex(String data) {
        return sha256Hex(data.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] data) {
        return digestHex("SHA-256", data);
    }

    private static String digestHex(String algorithm, byte[] data) {
        try {
            return toHex(MessageDigest.getInstance(algorithm).digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 不支持的摘要算法：" + algorithm, e);
        }
    }

    // ========== HMAC ==========

    public static byte[] hmacSha256(byte[] key, String data) {
        return hmac("HmacSHA256", key, data);
    }

    public static String hmacSha256Hex(String key, String data) {
        return hmacSha256Hex(key.getBytes(StandardCharsets.UTF_8), data);
    }

    public static String hmacSha256Hex(byte[] key, String data) {
        return toHex(hmacSha256(key, data));
    }

    public static byte[] hmacSha1(byte[] key, String data) {
        return hmac("HmacSHA1", key, data);
    }

    public static String hmacSha1Hex(byte[] key, String data) {
        return toHex(hmacSha1(key, data));
    }

    private static byte[] hmac(String algorithm, byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key, algorithm));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC 计算失败：" + algorithm, e);
        }
    }

    // ========== AES ==========

    /**
     * AES 解密（Base64 输入），返回字符串
     */
    public static String aesDecryptStr(byte[] key, String data) {
        return new String(aesDecrypt(key, Base64.getDecoder().decode(data)), StandardCharsets.UTF_8);
    }

    /**
     * AES 加密并输出 Base64
     */
    public static String aesEncryptBase64(byte[] key, String data) {
        return Base64.getEncoder().encodeToString(aesEncrypt(key, data.getBytes(StandardCharsets.UTF_8)));
    }

    public static byte[] aesDecrypt(byte[] key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ECB_PKCS5);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }

    public static byte[] aesEncrypt(byte[] key, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(AES_ECB_PKCS5);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    // ========== RSA ==========

    /**
     * RSA 私钥解密（按块分段，兼容密文为多块拼接的场景）
     */
    public static byte[] rsaDecrypt(String privateKeyBase64, byte[] data) {
        try {
            PrivateKey privateKey = buildPrivateKey(privateKeyBase64);
            Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return doFinalByBlock(cipher, data, cipher.getBlockSize());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA 解密失败", e);
        }
    }

    /**
     * RSA 公钥加密并输出 Base64（按块分段拼接后整体编码）
     */
    public static String rsaEncryptBase64(String publicKeyBase64, byte[] data) {
        try {
            PublicKey publicKey = buildPublicKey(publicKeyBase64);
            Cipher cipher = Cipher.getInstance(RSA_ECB_PKCS1);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            // PKCS1 填充每个块占用 11 字节
            int encryptBlockSize = cipher.getBlockSize() - 11;
            return Base64.getEncoder().encodeToString(doFinalByBlock(cipher, data, encryptBlockSize));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA 加密失败", e);
        }
    }

    /**
     * 分块执行 Cipher，规避单次 doFinal 的长度限制
     */
    private static byte[] doFinalByBlock(Cipher cipher, byte[] data, int blockSize) throws GeneralSecurityException {
        if (blockSize <= 0) {
            return cipher.doFinal(data);
        }
        List<byte[]> blocks = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += blockSize) {
            int length = Math.min(blockSize, data.length - offset);
            blocks.add(cipher.doFinal(data, offset, length));
        }
        int totalLength = blocks.stream().mapToInt(block -> block.length).sum();
        byte[] result = new byte[totalLength];
        int index = 0;
        for (byte[] block : blocks) {
            System.arraycopy(block, 0, result, index, block.length);
            index += block.length;
        }
        return result;
    }

    private static PrivateKey buildPrivateKey(String base64) throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private static PublicKey buildPublicKey(String base64) throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    // ========== 编码 ==========

    public static String toHex(byte[] data) {
        StringBuilder builder = new StringBuilder(data.length * 2);
        for (byte item : data) {
            builder.append(HEX_CHARS[(item >> 4) & 0x0F]);
            builder.append(HEX_CHARS[item & 0x0F]);
        }
        return builder.toString();
    }

}
