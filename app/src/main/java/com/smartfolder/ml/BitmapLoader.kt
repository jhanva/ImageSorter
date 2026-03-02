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
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            }

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            // Calculate optimal sample size to land close to TARGET_SIZE
            val maxDim = maxOf(origWidth, origHeight)
            var sampleSize = 1
            while (maxDim / (sampleSize * 2) >= TARGET_SIZE) {
                sampleSize *= 2
            }

            // Pass 2: decode with density-based scaling to hit TARGET_SIZE directly
            // This avoids creating an intermediate bitmap and then scaling it
            val sampledWidth = origWidth / sampleSize
            val sampledHeight = origHeight / sampleSize
            val sampledMaxDim = maxOf(sampledWidth, sampledHeight)

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                // Use density scaling to resize during decode instead of post-scaling
                if (sampledMaxDim > TARGET_SIZE) {
                    inScaled = true
                    inDensity = sampledMaxDim
                    inTargetDensity = TARGET_SIZE
                }
            }

            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }
}
