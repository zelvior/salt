package com.zelvior.salt.core.notifications

import java.util.regex.Pattern

/**
 * Local privacy filter applied to notification text BEFORE any data touches the AI layer.
 *
 * Strips likely OTP / one-time codes, passwords, emails and phone numbers, and drops
 * notifications whose package matches a user blacklist. The regexes are intentionally
 * conservative heuristics — Phase 2 will let users tune them via the UI.
 */
class NotificationSanitizer(
    private val packageBlacklist: Set<String> = emptySet(),
    private val extraPatterns: List<Pattern> = emptyList()
) {
    fun isBlacklisted(packageName: String?): Boolean =
        packageName != null && packageBlacklist.contains(packageName)

    fun sanitize(text: String): String {
        var out = text
        for (pattern in ALL_PATTERNS + extraPatterns) {
            out = pattern.matcher(out).replaceAll(REDACTED)
        }
        return out
    }

    companion object {
        const val REDACTED = "••••"

        // OTP / one-time code / password phrases followed by the secret value.
        private val OTP = Pattern.compile(
            "(?i)\\b(?:otp|one[- ]?time(?: password)?|verification code|security code|login code|passcode|pin)\\b[\\s:=-]*\\S+"
        )
        private val EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        private val PHONE = Pattern.compile("\\b\\+?\\d[\\d\\s().-]{7,}\\d\\b")

        private val ALL_PATTERNS = listOf(OTP, EMAIL, PHONE)
    }
}
