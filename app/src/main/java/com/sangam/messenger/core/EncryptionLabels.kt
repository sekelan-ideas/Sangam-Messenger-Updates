package com.sangam.messenger.core

import com.sangam.messenger.BuildConfig

/**
 * Single source of truth for encryption wording across Android UI screens.
 * Flip BuildConfig.ENCRYPTION_VERIFIED (app/build.gradle.kts) to true ONLY
 * after the real-device acceptance test plan passes.
 */
object EncryptionLabels {
    val isVerified: Boolean
        get() = BuildConfig.ENCRYPTION_VERIFIED

    val shortBadge: String
        get() = if (isVerified) {
            "End-to-end encrypted"
        } else {
            "Signal Protocol sessions, sent over TLS"
        }

    val fullNotice: String
        get() = if (isVerified) {
            "End-to-end encrypted: message, media and call content only the people in the chat can read. Profile info and who you talk to are visible to Sangam."
        } else {
            "Signal Protocol sessions, sent over TLS"
        }

    val callBadge: String
        get() = if (isVerified) {
            "\uD83D\uDD12 Sangam Confluence Call · End-to-end encrypted"
        } else {
            "\uD83D\uDD12 Sangam Confluence Call · Signal Protocol sessions, sent over TLS"
        }

    val callFeatureSummary: String
        get() = if (isVerified) {
            "End-to-end encrypted voice & video calls over any network"
        } else {
            "Voice & video calls with Signal Protocol sessions, sent over TLS"
        }
}
