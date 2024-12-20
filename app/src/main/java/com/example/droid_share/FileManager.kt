package com.example.droid_share

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class RxFileDescriptor {
    var fileNameReceived = ""
    var fileNameSaved = ""
    var fileSize = 0
}

data class TxFileDescriptor(
    val fileName: String,
    val fileSize: Int,
    val inputStream: InputStream
) {}

class TxFilePackDescriptor() {
    var size = 0
    var dscrs  = mutableListOf<TxFileDescriptor>()

    fun copy(): TxFilePackDescriptor {
        val copy = TxFilePackDescriptor()
        copy.size = this.size
        copy.dscrs = this.dscrs.toMutableList()
        return copy
    }
    fun clear() {
        size = 0
        dscrs.clear()
    }
    fun add(dscr: TxFileDescriptor) {
        size += dscr.fileSize
        dscrs.add(dscr)
    }
    fun isEmpty(): Boolean {
        return size == 0
    }
    fun isNotEmpty(): Boolean {
        return size > 0
    }
    override fun toString(): String {
        val sb = StringBuilder()
            .append("size = $size\n")
        for (dscr in dscrs) {
            sb.append(" - $dscr\n")
        }
        return sb.toString()
    }
}

class RxFilePackDescriptor() {
    var numFiles = 0
    var sizeTotal = 0
    var sizeReceived = 0
    var dscrs = mutableListOf<RxFileDescriptor>()

    fun clear() {
        numFiles = 0
        sizeTotal = 0
        sizeReceived = 0
        dscrs.clear()
    }
    fun add(dscr: RxFileDescriptor) {
        dscrs.add(dscr)
    }
    fun isReceptionFinished(): Boolean {
        return dscrs.size == numFiles
    }
    fun getReceivedPercent(): Float {
        return sizeReceived.toFloat() / sizeTotal.toFloat() * 100f
    }
}

class FileManager {

    companion object {

        private const val TAG = "FileManager"
        private val SAVE_FILE_DIR = Environment.getExternalStorageDirectory().toString() + "/Download/"

        fun getSaveFileName(fileName: String) : String{
            var fileNameUpd = ""
            val pair = getFileNameAndExtension(fileName)
            val name = pair.first
            val extension = pair.second
            var cntr = 0
            do {
                fileNameUpd = name +
                        if (cntr++ > 0) { "($cntr)" } else { "" } +
                        if (extension.isNotEmpty()) { ".$extension" } else { "" }
                val filePath =getFullPath(fileNameUpd)
            } while (File(filePath).exists())

            return fileNameUpd
        }

        fun getOutFileStream(fileName: String) : FileOutputStream{
            val filePath = getFullPath(fileName)
            val file = File(filePath)
            if (!file.createNewFile()) {
                throw Exception("name '$filePath' is already exists")
            }
            return FileOutputStream(file)
        }

        fun deleteReceivedFiles(dscrs : MutableList<RxFileDescriptor>) {
            dscrs.forEach() { dscr ->
                deleteFile(dscr.fileNameSaved)
            }
        }

        fun deleteFile(fileName: String) {
            val file = File(getFullPath(fileName))
            if (file.exists()) {
                file.delete()
            }
        }

        @SuppressLint("Range")
        fun getTxFileDescriptor(context : Context, uri: Uri): TxFileDescriptor {
            val cursor = context.contentResolver?.query(uri, null, null, null, null)
            cursor?.moveToFirst()
            val fileName = cursor?.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
            val fileSize = cursor?.getString(cursor.getColumnIndex(OpenableColumns.SIZE))?.toInt()
            cursor?.close()
            val inputStream = context.contentResolver?.openInputStream(uri)

            return TxFileDescriptor(fileName!!, fileSize!!, inputStream!!)
        }

        private fun getFullPath(fileName : String) : String {
            return SAVE_FILE_DIR + fileName
        }

        private fun getFileNameAndExtension(fileName : String) : Pair<String, String> {
            var name = fileName
            var extension = ""
            val id = fileName.lastIndexOf(".")
            if (id != -1) {
                name = fileName.substring(0, id)
                extension = fileName.substring(id+1)
            }
            return Pair(name, extension)
        }

    }
}