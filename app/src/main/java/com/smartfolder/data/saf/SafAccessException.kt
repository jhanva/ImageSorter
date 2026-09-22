package com.smartfolder.data.saf

/**
 * Raised when a folder or its children cannot be read through SAF. Listing
 * used to swallow these and return an empty list, which made a permission or
 * provider failure look exactly like an empty trash folder.
 */
class SafAccessException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
