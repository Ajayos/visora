package dev.visora

import android.content.*
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

class UpdateManager(private val context: Context) {
    val staged: File
        get() = File(context.filesDir, "update.apk")

    @Suppress("DEPRECATION")
    fun verifyAndStage(candidate: File, expectedHash: String) {
        require(
            Regex("[a-f0-9]{64}").matches(expectedHash) &&
                PromotionEngine.digest(candidate) == expectedHash,
        )
        val pm = context.packageManager
        val incoming =
            pm.getPackageArchiveInfo(candidate.path, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: error("Invalid APK")
        val installed =
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        require(
            incoming.packageName == context.packageName &&
                incoming.longVersionCode > installed.longVersionCode,
        ) {
            "Package mismatch or rollback"
        }
        val oldSigners =
            installed.signingInfo
                ?.apkContentsSigners
                ?.map { SecurityPolicy.hash(it.toByteArray()) }
                ?.toSet()
        val newSigners =
            incoming.signingInfo
                ?.apkContentsSigners
                ?.map { SecurityPolicy.hash(it.toByteArray()) }
                ?.toSet()
        require(!oldSigners.isNullOrEmpty() && newSigners == oldSigners) { "APK signer mismatch" }
        if (candidate != staged) check(candidate.renameTo(staged))
    }

    fun requestInstall(): Boolean {
        if (!staged.exists()) return false
        // PackageInstaller/Fire OS confirmation is mandatory on a normal, unprivileged device.
        return try {
            context.startActivity(
                Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(
                        Uri.parse("content://dev.visora.tv.updates/update.apk"),
                        "application/vnd.android.package-archive",
                    )
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK,
                    ),
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}

class UpdateProvider : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File {
        require(uri.path == "/update.apk")
        return File(requireNotNull(context).filesDir, "update.apk")
    }

    override fun getType(uri: Uri) = "application/vnd.android.package-archive"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        require(mode == "r")
        return ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        val f = file(uri)
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            addRow(arrayOf("VISORA.apk", f.length()))
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = throw UnsupportedOperationException()
}
