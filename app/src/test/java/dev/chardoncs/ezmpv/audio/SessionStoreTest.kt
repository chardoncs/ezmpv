package dev.chardoncs.ezmpv.audio

import dev.chardoncs.ezmpv.player.PlaySequence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionStoreTest {

    @Test
    fun decode_corruptJsonReturnsNull() {
        assertNull(SessionCodec.decode("not json"))
    }

    @Test
    fun playSequenceToSavedName_roundTripsAllModes() {
        for (mode in PlaySequence.entries) {
            assertEquals(mode, savedNameToPlaySequence(mode.toSavedName()))
        }
    }

    @Test
    fun savedNameToPlaySequence_unknownFallsBackToSequence() {
        assertEquals(PlaySequence.SEQUENCE, savedNameToPlaySequence("BOGUS"))
        assertEquals(PlaySequence.SEQUENCE, savedNameToPlaySequence(""))
    }
}