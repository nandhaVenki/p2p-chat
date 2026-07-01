package com.example.p2pchat.util

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object SdpCompressor {
    
    fun compress(sdp: String): String {
        val bos = ByteArrayOutputStream()
        val gos = GZIPOutputStream(bos)
        gos.write(sdp.toByteArray())
        gos.close()
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun decompress(compressed: String): String {
        val bytes = Base64.decode(compressed, Base64.NO_WRAP or Base64.URL_SAFE)
        val bis = ByteArrayInputStream(bytes)
        val gis = GZIPInputStream(bis)
        val bos = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        var len: Int
        while (gis.read(buffer).also { len = it } > 0) {
            bos.write(buffer, 0, len)
        }
        return bos.toString()
    }
}
