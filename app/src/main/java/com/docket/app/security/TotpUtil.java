package com.docket.app.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Clean RFC 6238 TOTP implementation and RFC 4648 Base32 helper.
 */
public final class TotpUtil {
    private static final String HMAC_ALGO = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final int MODULO = 1_000_000;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpUtil() {
    }

    public static String generateSecret() {
        byte[] buffer = new byte[20];
        RANDOM.nextBytes(buffer);
        return encodeBase32(buffer);
    }

    public static List<String> generateBackupCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] bytes = new byte[4];
            RANDOM.nextBytes(bytes);
            codes.add(HexFormat.of().formatHex(bytes).toUpperCase());
        }
        return codes;
    }

    public static boolean verifyCode(String base32Secret, String inputCode) {
        if (base32Secret == null || inputCode == null) return false;
        String cleanCode = inputCode.replaceAll("\\s+", "");
        if (cleanCode.length() != CODE_DIGITS) return false;

        int expected;
        try {
            expected = Integer.parseInt(cleanCode);
        } catch (NumberFormatException e) {
            return false;
        }

        byte[] key = decodeBase32(base32Secret);
        long currentInterval = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;

        // Allow +/- 1 step clock skew (window of 90 seconds)
        for (int i = -1; i <= 1; i++) {
            if (generateCodeForInterval(key, currentInterval + i) == expected) {
                return true;
            }
        }
        return false;
    }

    public static String getQrUri(String secret, String email) {
        return "otpauth://totp/Docket:" + email + "?secret=" + secret + "&issuer=Docket";
    }

    static int generateCodeForInterval(byte[] key, long interval) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(interval).array();
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            return binary % MODULO;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to calculate TOTP", e);
        }
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                bitsLeft -= 5;
                out.append(BASE32_CHARS.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    public static byte[] decodeBase32(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8 + 1);
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < clean.length(); i++) {
            int val = BASE32_CHARS.indexOf(clean.charAt(i));
            if (val < 0) continue;
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                out.put((byte) ((buffer >> bitsLeft) & 0xFF));
            }
        }
        byte[] res = new byte[out.position()];
        out.flip();
        out.get(res);
        return res;
    }
}
