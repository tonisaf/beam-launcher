package com.home.tiles

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun comparesNumericVersionsAndRejectsNonStableTags() {
        assertTrue(newerRelease("v0.10", "0.9"))
        assertTrue(newerRelease("v1.0.1", "1"))
        assertFalse(newerRelease("v1.0.0", "1.0"))
        assertFalse(newerRelease("v0.3", "0.4"))
        assertFalse(newerRelease("v1.0-beta", "0.4"))
        assertFalse(newerRelease("v999999999999", "0.4"))
    }
    @Test fun onlyAcceptsCanonicalRepositoryApkUrls() {
        val good = "https://github.com/tonisaf/beam-launcher/releases/download/v0.4/app-release.apk"
        assertTrue(validUpdateUrl(good))
        assertFalse(validUpdateUrl(good.replace("https", "http")))
        assertFalse(validUpdateUrl(good.replace("github.com", "github.com.evil.test")))
        assertFalse(validUpdateUrl(good.replace("tonisaf", "another")))
        assertFalse(validUpdateUrl(good.replace("app-release.apk", "stub-button1-release.apk")))
        assertFalse(validUpdateUrl(good + "?redirect=evil"))
        assertFalse(validUpdateUrl(good.replace("v0.4", "../v0.4")))
    }
    @Test fun requiresAFullSha256Digest() {
        assertEquals("a".repeat(64), updateDigest("sha256:" + "A".repeat(64)))
        assertNull(updateDigest(""))
        assertNull(updateDigest("sha256:1234"))
        assertNull(updateDigest("g".repeat(64)))
    }
}
