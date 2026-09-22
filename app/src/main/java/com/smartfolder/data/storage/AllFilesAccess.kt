package com.smartfolder.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * All files access. Destinations are every image folder on the device, so the
 * app writes outside anything the user picked through SAF, and only this
 * permission allows that. Android does not grant it from a dialog: the user
 * has to turn it on in settings, and inside Secure Folder it has to be granted
 * again in that profile.
 */
@Singleton
class AllFilesAccess @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun isGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Settings screens where the permission is turned on. The per app screen is
     * not available on every device, so the caller falls back to the general
     * list when the first intent cannot be resolved.
     */
    fun settingsIntents(): List<Intent> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        return listOf(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        )
    }
}
