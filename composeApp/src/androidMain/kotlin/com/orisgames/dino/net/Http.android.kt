package com.orisgames.dino.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI

actual suspend fun httpGetText(url: String): String = withContext(Dispatchers.IO) {
    request(url, "GET", null)
}

actual suspend fun httpPostText(url: String, body: String): String = withContext(Dispatchers.IO) {
    request(url, "POST", body)
}

private fun request(url: String, method: String, body: String?): String {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            connection.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = connection.responseCode
        if (code !in 200..299) error("HTTP $code from $url")
        return connection.inputStream.use { it.readBytes().decodeToString() }
    } finally {
        connection.disconnect()
    }
}
