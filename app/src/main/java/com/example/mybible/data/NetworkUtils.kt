package com.example.mybible.data

import java.net.HttpURLConnection
import java.net.URL

/** Downloads a URL's full body as text, or null on any failure. */
internal fun fetchTextOrNull(url: String, timeoutMs: Int = 30_000): String? {
    return try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
        }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
