package dev.chardoncs.ezmpv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaySequenceTest {

    @Test
    fun next_cyclesThroughAllModesAndWraps() {
        val cycle = generateSequence(PlaySequence.SEQUENCE) { it.next() }
            .take(PlaySequence.entries.size + 1)
            .toList()
        assertEquals(PlaySequence.entries.toList(), cycle.dropLast(1))
        assertEquals(PlaySequence.SEQUENCE, cycle.last())
    }

    @Test
    fun indexAfterTrackEnd_sequenceStopsAtEnd() {
        assertNull(indexAfterTrackEnd(PlaySequence.SEQUENCE, 3, 2))
        assertEquals(1, indexAfterTrackEnd(PlaySequence.SEQUENCE, 3, 0))
    }

    @Test
    fun indexAfterTrackEnd_repeatAllWraps() {
        assertEquals(0, indexAfterTrackEnd(PlaySequence.REPEAT_ALL, 3, 2))
        assertEquals(1, indexAfterTrackEnd(PlaySequence.REPEAT_ALL, 3, 0))
    }

    @Test
    fun indexAfterTrackEnd_repeatOneReplaysCurrent() {
        assertEquals(2, indexAfterTrackEnd(PlaySequence.REPEAT_ONE, 3, 2))
    }

    @Test
    fun indexAfterTrackEnd_shuffleStopsAtEnd() {
        assertNull(indexAfterTrackEnd(PlaySequence.SHUFFLE, 3, 2))
        assertEquals(1, indexAfterTrackEnd(PlaySequence.SHUFFLE, 3, 0))
    }

    @Test
    fun indexAfterTrackEnd_shuffleRepeatWraps() {
        assertEquals(0, indexAfterTrackEnd(PlaySequence.SHUFFLE_REPEAT, 3, 2))
    }

    @Test
    fun indexAfterTrackEnd_casinoNeverPicksCurrent() {
        repeat(50) { seed ->
            val p = indexAfterTrackEnd(PlaySequence.CASINO, 5, 2, Random(seed.toLong()))
            assertTrue("seed=$seed pick=$p", p != null && p != 2)
        }
    }

    @Test
    fun indexAfterTrackEnd_casinoSingleItemReplaysIt() {
        assertEquals(0, indexAfterTrackEnd(PlaySequence.CASINO, 1, 0))
    }

    @Test
    fun indexForSkip_sequenceClampsAtEnds() {
        assertNull(indexForSkip(PlaySequence.SEQUENCE, 3, 2, forward = true))
        assertNull(indexForSkip(PlaySequence.SEQUENCE, 3, 0, forward = false))
        assertEquals(2, indexForSkip(PlaySequence.SEQUENCE, 3, 1, forward = true))
        assertEquals(0, indexForSkip(PlaySequence.SEQUENCE, 3, 1, forward = false))
    }

    @Test
    fun indexForSkip_repeatAllWrapsBothDirections() {
        assertEquals(0, indexForSkip(PlaySequence.REPEAT_ALL, 3, 2, forward = true))
        assertEquals(2, indexForSkip(PlaySequence.REPEAT_ALL, 3, 0, forward = false))
    }

    @Test
    fun indexForSkip_repeatOneSkipsLinearly() {
        assertEquals(2, indexForSkip(PlaySequence.REPEAT_ONE, 3, 1, forward = true))
        assertEquals(0, indexForSkip(PlaySequence.REPEAT_ONE, 3, 1, forward = false))
        assertNull(indexForSkip(PlaySequence.REPEAT_ONE, 3, 2, forward = true))
    }

    @Test
    fun indexForSkip_casinoNeverPicksCurrent() {
        repeat(50) { seed ->
            val p = indexForSkip(PlaySequence.CASINO, 5, 2, forward = true, random = Random(seed.toLong()))
            assertTrue(p != null && p != 2)
        }
    }
}