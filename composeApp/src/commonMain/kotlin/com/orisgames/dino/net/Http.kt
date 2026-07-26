package com.orisgames.dino.net

/** Minimal text-over-HTTP helpers; each platform provides its own transport. */
expect suspend fun httpGetText(url: String): String

expect suspend fun httpPostText(url: String, body: String): String
