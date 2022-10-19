package d.d.meshenger

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.*
import java.util.regex.Pattern

internal object Utils {
    fun getThreadInfo(): String {
        val thread =  Thread.currentThread()
        return "@[name=${thread.name}, id=${thread.id}]"
    }

    fun hasReadPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasWritePermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(activity: Activity): Boolean {
        return ContextCompat.checkSelfPermission(
            activity, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestCameraPermission(activity: Activity, request_code: Int) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(
                Manifest.permission.CAMERA
            ), request_code
        )
    }

    fun requestReadPermission(activity: Activity, request_code: Int) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            ), request_code
        )
    }

    fun requestWritePermission(activity: Activity, request_code: Int) {
        ActivityCompat.requestPermissions(
            activity, arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ), request_code
        )
    }

    fun allGranted(grantResults: IntArray): Boolean {
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return true
    }

    fun split(str: String): List<String> {
        return str.split("\\s*,\\s*")
    }

    private val NAME_PATTERN = Pattern.compile("[\\w][\\w _-]{1,22}[\\w]")

    // check for a name that has no funny unicode characters to not let them look to much like other names
    fun isValidName(name: String?): Boolean {
        if (name == null || name.length == 0) {
            return false
        }
        return NAME_PATTERN.matcher(name).matches()
    }

    private val hexArray = "0123456789ABCDEF".toCharArray()

    fun byteArrayToHexString(bytes: ByteArray?): String {
        if (bytes == null) {
            return ""
        }
        val hexChars = CharArray(bytes.size * 2)
        var j = 0
        while (j < bytes.size) {
            val v: Int = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
            j += 1
        }
        return String(hexChars)
    }

    fun hexStringToByteArray(str: String?): ByteArray? {
        if (str == null) {
            return null
        }
        val len = str.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(str[i], 16) shl 4)
                    + Character.digit(str[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    // write file to external storage
    @Throws(IOException::class)
    fun writeExternalFile(filepath: String, data: ByteArray?) {
        val file = File(filepath)
        if (file.exists() && file.isFile) {
            if (!file.delete()) {
                throw IOException("Failed to delete existing file: $filepath")
            }
        }
        file.createNewFile()
        val fos = FileOutputStream(file)
        fos.write(data)
        fos.close()
    }

    // read file from external storage
    @Throws(IOException::class)
    fun readExternalFile(filepath: String): ByteArray {
        val file = File(filepath)
        if (!file.exists() || !file.isFile) {
            throw IOException("File does not exist: $filepath")
        }
        val fis = FileInputStream(file)
        var nRead: Int
        val data = ByteArray(16384)
        val buffer = ByteArrayOutputStream()
        while (fis.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        fis.close()
        return buffer.toByteArray()
    }


   fun getExternalFileSize(ctx: Context, uri: Uri?): Long {
        val cursor = ctx.contentResolver.query(uri!!, null, null, null, null)
        cursor!!.moveToFirst()
        val size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
        cursor.close()
        return size
    }

    @Throws(IOException::class)
    fun readExternalFile(ctx: Context, uri: Uri): ByteArray {
        val size = getExternalFileSize(ctx, uri).toInt()
        val isstream = ctx.contentResolver.openInputStream(uri)
        val buffer = ByteArrayOutputStream()
        var nRead = 0
        val dataArray = ByteArray(size)
        while (isstream != null && isstream.read(dataArray, 0, dataArray.size).also { nRead = it } != -1) {
            buffer.write(dataArray, 0, nRead)
        }
        isstream?.close()
        return dataArray
    }

    @Throws(IOException::class)
    fun writeExternalFile(ctx: Context, uri: Uri, dataArray: ByteArray) {
        val fos = ctx.contentResolver.openOutputStream(uri)
        fos!!.write(dataArray)
        fos.close()
    }

    // write file to external storage
    @Throws(IOException::class)
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
    @Throws(IOException::class)
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