package com.smartfolder.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class OnnxInputSizeTest {

    @Test
    fun `static shape resolves to its spatial size`() {
        assertEquals(384, MobileClipSession.resolveInputSize(longArrayOf(1, 3, 384, 384), fallback = 256))
    }

    @Test
    fun `dynamic shape falls back to the model default`() {
        assertEquals(384, MobileClipSession.resolveInputSize(longArrayOf(-1, 3, -1, -1), fallback = 384))
    }

    @Test
    fun `missing shape falls back to the model default`() {
        assertEquals(256, MobileClipSession.resolveInputSize(null, fallback = 256))
    }
}
