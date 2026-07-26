package com.orisgames.dino.net

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.fetch.RequestInit
import org.w3c.fetch.Response

actual suspend fun httpGetText(url: String): String {
    val response = window.fetch(url).await<Response>()
    if (!response.ok) error("HTTP ${response.status} from $url")
    return response.text().await<JsString>().toString()
}

actual suspend fun httpPostText(url: String, body: String): String {
    val response = window.fetch(
        url,
        RequestInit(method = "POST", body = body.toJsString()),
    ).await<Response>()
    if (!response.ok) error("HTTP ${response.status} from $url")
    return response.text().await<JsString>().toString()
}
