package com.example.p2pchat.util

import java.security.MessageDigest

object HashUtils {
    fun hashPhoneNumber(phoneNumber: String): String {
        // Clean phone number: remove any non-digits
        val cleanNumber = phoneNumber.replace(Regex("[^\\d]"), "")
        val bytes = cleanNumber.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
