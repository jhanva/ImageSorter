package com.smartfolder.domain.model

enum class ReviewStatus {
    PENDING,
    ACCEPTED,
    SKIPPED;

    companion object {
        fun fromName(raw: String): ReviewStatus =
            entries.firstOrNull { it.name == raw } ?: PENDING
    }
}
