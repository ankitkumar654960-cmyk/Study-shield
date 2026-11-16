package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityUtilsTest {

  @Test
  fun shieldPasswordHash_verifiesCorrectlyAndRejectsMismatch() {
    val uid = "user_abc"
    val hash = SecurityUtils.hashShieldPassword("focus2026", uid)
    assertTrue(SecurityUtils.verifyShieldPassword("focus2026", uid, hash))
    assertFalse(SecurityUtils.verifyShieldPassword("wrongpass", uid, hash))
    assertFalse(SecurityUtils.verifyShieldPassword("focus2026", "other_uid", hash))
  }

  @Test
  fun totpGenerator_producesSixDigitsAndVerifiesWithinWindow() {
    val secret = SecurityUtils.generateTotpSecret()
    assertEquals(16, secret.length)
    val now = 1_763_337_600_000L
    val code = SecurityUtils.generateTotpCode(secret, timeMillis = now)
    assertEquals(6, code.length)
    assertTrue(code.all { it.isDigit() })
    assertTrue(SecurityUtils.verifyTotpCode(secret, code, timeMillis = now))
    assertTrue(SecurityUtils.verifyTotpCode(secret, code, timeMillis = now + 29_000L))
    assertFalse(SecurityUtils.verifyTotpCode(secret, "abcdef", timeMillis = now))
  }
}
