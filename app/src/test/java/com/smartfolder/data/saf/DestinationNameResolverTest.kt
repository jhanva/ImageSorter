package com.smartfolder.data.saf

import org.junit.Assert.assertEquals
import org.junit.Test

class DestinationNameResolverTest {

    @Test
    fun `returns original name when no collision exists`() {
        val resolved = DestinationNameResolver.resolveUniqueDisplayName(
            existingDisplayNames = setOf("other.png"),
            requestedDisplayName = "raiden.png"
        )

        assertEquals("raiden.png", resolved)
    }

    @Test
    fun `appends numeric suffix when file already exists`() {
        val resolved = DestinationNameResolver.resolveUniqueDisplayName(
            existingDisplayNames = setOf("raiden.png"),
            requestedDisplayName = "raiden.png"
        )

        assertEquals("raiden (1).png", resolved)
    }

    @Test
    fun `skips occupied suffixes until an empty slot is found`() {
        val resolved = DestinationNameResolver.resolveUniqueDisplayName(
            existingDisplayNames = setOf(
                "raiden.png",
                "raiden (1).png",
                "raiden (2).png"
            ),
            requestedDisplayName = "raiden.png"
        )

        assertEquals("raiden (3).png", resolved)
    }
}
