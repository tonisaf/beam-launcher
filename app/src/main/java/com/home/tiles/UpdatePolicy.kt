package com.home.tiles

import java.net.URI

internal fun newerRelease(tag: String, installed: String): Boolean {
    fun parts(value: String): List<Int>? {
        val normalized = value.removePrefix("v")
        if (!normalized.matches(Regex("[0-9]+(\\.[0-9]+){0,3}"))) return null
        return normalized.split('.').map { it.toIntOrNull() ?: return null }
    }
    val remote = parts(tag) ?: return false
    val local = parts(installed) ?: return false
    for (i in 0 until maxOf(remote.size, local.size)) {
        val comparison = (remote.getOrElse(i) { 0 }).compareTo(local.getOrElse(i) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return false
}

internal fun validUpdateUrl(value: String): Boolean = runCatching {
    val uri = URI(value)
    uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
        uri.userInfo == null && uri.query == null && uri.fragment == null &&
        uri.rawPath.startsWith("/tonisaf/beam-launcher/releases/download/") &&
        uri.rawPath.endsWith("/app-release.apk") && !uri.rawPath.contains("..")
}.getOrDefault(false)

internal fun updateDigest(value: String): String? = value.removePrefix("sha256:")
    .takeIf { it.matches(Regex("[a-fA-F0-9]{64}")) }?.lowercase()
