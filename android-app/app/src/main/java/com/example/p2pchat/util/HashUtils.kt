package com.example.p2pchat.util

import java.security.MessageDigest

data class PhoneRoutingInfo(
    val countryCode: String,
    val areaCode: String,
    val subscriberHash: String
)

object HashUtils {
    fun hashPhoneNumber(phoneNumber: String): String {
        val cleanNumber = phoneNumber.replace(Regex("[^\\d]"), "")
        val bytes = cleanNumber.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    fun parsePhoneRoutingInfo(phoneNumber: String): PhoneRoutingInfo {
        // Remove spaces, hyphens, and parentheses
        val clean = phoneNumber.trim().replace(Regex("[\\s\\-\\(\\)]"), "")
        var country = ""
        var remaining = ""
        
        if (clean.startsWith("+")) {
            val digits = clean.substring(1)
            if (digits.startsWith("1") || digits.startsWith("7")) {
                country = "+" + digits.substring(0, 1)
                remaining = digits.substring(1)
            } else if (digits.length >= 2) {
                country = "+" + digits.substring(0, 2)
                remaining = digits.substring(2)
            } else {
                country = "+" + digits
                remaining = ""
            }
        } else {
            // Local fallback
            country = "+0"
            remaining = clean
        }
        
        // Extract area code (first 3 digits of remaining number)
        val area = if (remaining.length >= 3) {
            remaining.substring(0, 3)
          } else {
            remaining
          }
          
        val subscriber = if (remaining.length > 3) {
            remaining.substring(3)
        } else {
            ""
        }
        
        val subscriberHash = hashString(subscriber)
        return PhoneRoutingInfo(country, area, subscriberHash)
    }

    private fun hashString(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}
