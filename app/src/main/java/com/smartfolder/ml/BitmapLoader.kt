package com.smartfolder.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitmapLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TARGET_SIZE = 384
    }

    fun loadForEmbedding(uri: Uri): Bitmap? {
        return try {
            // Pass 1: decode bounds only
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            }

            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            // Calculate sample size
            val maxDim = maxOf(options.outWidth, options.outHeight)
            var sampleSize = 1
            while (maxDim / sampleSize > TARGET_SIZE * 2) {
                sampleSize *= 2
            }

            // Pass 2: decode with sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: return null

            // Scale to target size
            val scale = TARGET_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)
            if (scale < 1f) {
                val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                if (scaled !== bitmap) bitmap.recycle()
                scaled
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }
}
