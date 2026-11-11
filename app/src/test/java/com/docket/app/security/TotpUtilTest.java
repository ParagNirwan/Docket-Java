package com.docket.app.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TotpUtilTest {
    @Test
    void generateCodeForInterval_matchesRfc6238Sha1TestVectors() {
        byte[] key = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        assertEquals(287082, TotpUtil.generateCodeForInterval(key, 1));
        assertEquals(81_804, TotpUtil.generateCodeForInterval(key, 37_037_036));
        assertEquals(50_471, TotpUtil.generateCodeForInterval(key, 37_037_037));
        assertEquals(5_924, TotpUtil.generateCodeForInterval(key, 41_152_263));
        assertEquals(279037, TotpUtil.generateCodeForInterval(key, 66_666_666));
        assertEquals(353130, TotpUtil.generateCodeForInterval(key, 666_666_666));
    }
}
