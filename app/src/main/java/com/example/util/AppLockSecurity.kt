package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object AppLockSecurity {
    private val random = SecureRandom()

    fun generateSaltHex(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPin(pin: String, saltHex: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var current = "$saltHex:$pin".toByteArray(Charsets.UTF_8)
        repeat(1000) {
            digest.reset()
            current = digest.digest(current)
        }
        return current.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(pin: String, expectedHashHex: String, saltHex: String): Boolean {
        if (pin.length != 4 || expectedHashHex.isBlank() || saltHex.isBlank()) return false
        val actual = hashPin(pin, saltHex).toByteArray(Charsets.UTF_8)
        val expected = expectedHashHex.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actual, expected)
    }
}
