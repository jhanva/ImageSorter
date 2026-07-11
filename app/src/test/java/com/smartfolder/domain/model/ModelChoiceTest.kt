package com.smartfolder.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelChoiceTest {

    @Test
    fun `default model is the semantic CLIP backend`() {
        assertEquals(ModelChoice.SEMANTIC, ModelChoice.DEFAULT)
        assertEquals(ModelBackend.ONNX_CLIP, ModelChoice.DEFAULT.backend)
    }

    @Test
    fun `anime model uses the CCIP feature extractor over the ONNX backend`() {
        assertEquals("ccip_caformer_24_feat.onnx", ModelChoice.ANIME.modelFileName)
        assertEquals(ModelBackend.ONNX_CLIP, ModelChoice.ANIME.backend)
        assertEquals(384, ModelChoice.ANIME.onnxInputFallback)
    }

    @Test
    fun `semantic model keeps its clip input fallback`() {
        assertEquals(256, ModelChoice.SEMANTIC.onnxInputFallback)
    }
}
