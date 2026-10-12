package com.home.tiles

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class VoiceRequestsTest {
    @After fun cleanup() { VoiceRequests.disable() }

    @Test fun disablingCancelsRecordingAndInvalidatesPendingResults() {
        val owner = Any()
        var cancelled = 0
        var keyEvents = 0
        val generation = VoiceRequests.generation
        VoiceRequests.claim(owner, onDisabled = { cancelled++ }) { keyEvents++ }
        VoiceRequests.onVoiceKey?.invoke(true)
        VoiceRequests.disable()
        VoiceRequests.onVoiceKey?.invoke(false)
        assertEquals(1, cancelled)
        assertEquals(1, keyEvents)
        assertNull(VoiceRequests.onVoiceKey)
        assertNotEquals(generation, VoiceRequests.generation)
        VoiceRequests.disable()
        assertEquals(1, cancelled)
    }

    @Test fun anOlderRequestCannotReleaseTheNewOwner() {
        val first = Any()
        val second = Any()
        var cancelled = false
        VoiceRequests.claim(first, onDisabled = {}) {}
        VoiceRequests.claim(second, onDisabled = { cancelled = true }) {}
        VoiceRequests.release(first)
        assertNotNull(VoiceRequests.onVoiceKey)
        VoiceRequests.disable()
        assertTrue(cancelled)
    }

    @Test fun aFinishedRequestIsNotCancelledAgain() {
        val owner = Any()
        var cancelled = false
        VoiceRequests.claim(owner, onDisabled = { cancelled = true }) {}
        VoiceRequests.release(owner)
        VoiceRequests.disable()
        assertFalse(cancelled)
        assertNull(VoiceRequests.onVoiceKey)
    }
}
