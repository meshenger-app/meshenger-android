package d.d.meshenger

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import java.io.*
import java.util.regex.Pattern

internal object Utils {
    fun getThreadInfo(): String {
        val thread =  Thread.currentThread()
        return "@[name=${thread.name}, id=${thread.id}]"
    }

    fun assertIsTrue(condition: Boolean) {
        if (!condition) {
            throw AssertionError("Expected condition to be true")
        }
    }

    fun checkIsOnMainThread() {
        if (Thread.currentThread() != Looper.getMainLooper().thread) {
            throw IllegalStateException("Code must run on the main thread!")
        }
    }

    fun checkIsNotOnMainThread() {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            throw IllegalStateException("Code must not run on the main thread!")
        }
    }

    fun printStackTrace() {
        try {
            throw Exception("printStackTrace() called")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }

    private val NAME_PATTERN = Pattern.compile("[\\w]|[\\w][\\w _-]{0,22}[\\w]")

    // Check for a name that has no funny unicode characters
    // and to not let them look to much like other names.
    fun isValidName(name: String?): Boolean {
        if (name == null || name.isEmpty()) {
            return false
        }
        return NAME_PATTERN.matcher(name).matches()
    }

    fun byteArrayToHexString(bytes: ByteArray?): String {
        if (bytes == null) {
            return ""
        }

        return bytes.joinToString(separator = "") {
            eachByte -> "%02X".format(eachByte)
        }
    }

    fun hexStringToByteArray(str: String?): ByteArray {
        if (str == null || (str.length % 2) != 0 || !str.all { it in '0'..'9' || it in 'a'..'f' || it in 'A' .. 'F' }) {
            return ByteArray(0)
        }

        return str.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // write file to external storage
    fun writeExternalFile(filepath: String, bytes: ByteArray?) {
        val file = File(filepath)
        if (file.exists()) {
            if (!file.isFile) {
                throw IOException("Not a file: $filepath")
            }

            if (!file.delete()) {
                throw IOException("Failed to delete existing file: $filepath")
            }
        }

        file.createNewFile()
        val fos = FileOutputStream(file)
        fos.write(bytes)
        fos.close()
    }

    // read file from external storage
    fun readExternalFile(filepath: String): ByteArray {
        val file = File(filepath)
        if (!file.exists()) {
            throw IOException("File does not exist: $filepath")
        }

        if (!file.isFile) {
             throw IOException("Not a file: $filepath")
        }

        val fis = FileInputStream(file)
        val bytes = fis.readAllBytes()
        fis.close()
        return bytes
    }

   fun getExternalFileSize(ctx: Context, uri: Uri?): Long {
        val cursor = ctx.contentResolver.query(uri!!, null, null, null, null)
        cursor!!.moveToFirst()
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (index >= 0) {
            val size = cursor.getLong(index)
            cursor.close()
            return size
        } else {
            cursor.close()
            return -1
        }
    }

    fun readExternalFile(ctx: Context, uri: Uri): ByteArray {
        val size = getExternalFileSize(ctx, uri).toInt()
        val isstream = ctx.contentResolver.openInputStream(uri)
        val buffer = ByteArrayOutputStream()
        var nRead = 0
        val dataArray = ByteArray(size)
        if (isstream != null) {
            while (isstream.read(dataArray, 0, dataArray.size).also { nRead = it } != -1 && nRead > 0) {
                buffer.write(dataArray, 0, nRead)
            }
        }
        isstream?.close()
        return dataArray
    }

    fun writeExternalFile(ctx: Context, uri: Uri, dataArray: ByteArray) {
        val fos = ctx.contentResolver.openOutputStream(uri)
        fos!!.write(dataArray)
        fos.close()
    }

    // write file to external storage
    fun writeInternalFile(filePath: String, dataArray: ByteArray) {
        val file = File(filePath)
        if (file.exists() && file.isFile) {
            if (!file.delete()) {
                throw IOException("Failed to delete existing file: $filePath")
            }
        }
        file.createNewFile()
        val fos = FileOutputStream(file)
        fos.write(dataArray)
        fos.close()
    }

    // read file from external storage
    fun readInternalFile(filePath: String): ByteArray {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            throw IOException("File does not exist: $filePath")
        }
        val fis = FileInputStream(file)
        var nRead: Int
        val dataArray = ByteArray(16384)
        val buffer = ByteArrayOutputStream()
        while (fis.read(dataArray, 0, dataArray.size).also { nRead = it } != -1) {
            buffer.write(dataArray, 0, nRead)
        }
        fis.close()
        return buffer.toByteArray()
    }
}
