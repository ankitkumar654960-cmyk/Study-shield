package com.example.util

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

object SecurityUtils {
  private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

  fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun hashShieldPassword(password: String, uid: String): String {
    return sha256("studyshield_v1:$uid:$password")
  }

  fun verifyShieldPassword(inputPassword: String, uid: String, storedHash: String): Boolean {
    if (storedHash.isBlank()) return false
    return hashShieldPassword(inputPassword, uid) == storedHash
  }

  fun generateTotpSecret(length: Int = 16): String {
    val random = SecureRandom()
    val sb = StringBuilder(length)
    repeat(length) {
      sb.append(BASE32_ALPHABET[random.nextInt(BASE32_ALPHABET.length)])
    }
    return sb.toString()
  }

  fun generateTotpCode(
      base32Secret: String,
      timeMillis: Long = System.currentTimeMillis(),
      periodSeconds: Long = 30L,
      digits: Int = 6,
  ): String {
    if (base32Secret.isBlank()) return "000000"
    val key = decodeBase32(base32Secret)
    if (key.isEmpty()) return "000000"
    val counter = (timeMillis / 1000L) / periodSeconds
    val data = ByteBuffer.allocate(8).putLong(counter).array()
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key, "HmacSHA1"))
    val hash = mac.doFinal(data)
    val offset = hash[hash.size - 1].toInt() and 0x0F
    val binary =
        ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
    val otp = binary % 10.0.pow(digits).toInt()
    return otp.toString().padStart(digits, '0')
  }

  fun verifyTotpCode(
      base32Secret: String,
      code: String,
      timeMillis: Long = System.currentTimeMillis(),
  ): Boolean {
    val sanitized = code.trim()
    if (sanitized.length != 6 || sanitized.any { !it.isDigit() }) return false
    // Accept current 30s window and +/- 1 window for clock skew tolerance
    for (offset in -1..1) {
      val candidate = generateTotpCode(base32Secret, timeMillis + offset * 30_000L)
      if (candidate == sanitized) return true
    }
    return false
  }

  fun secondsRemainingInTotpWindow(timeMillis: Long = System.currentTimeMillis()): Int {
    val elapsed = ((timeMillis / 1000L) % 30L).toInt()
    return 30 - elapsed
  }

  private fun decodeBase32(input: String): ByteArray {
    val clean = input.uppercase().replace("=", "").filter { it in BASE32_ALPHABET }
    if (clean.isEmpty()) return ByteArray(0)
    val output = ArrayList<Byte>()
    var buffer = 0
    var bitsLeft = 0
    for (ch in clean) {
      val value = BASE32_ALPHABET.indexOf(ch)
      buffer = (buffer shl 5) or value
      bitsLeft += 5
      if (bitsLeft >= 8) {
        output.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
        bitsLeft -= 8
      }
    }
    return output.toByteArray()
  }
}
