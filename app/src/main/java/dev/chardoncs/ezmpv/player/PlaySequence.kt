package dev.chardoncs.ezmpv.player

import kotlin.random.Random

enum class PlaySequence {
    SEQUENCE,
    REPEAT_ALL,
    REPEAT_ONE,
    SHUFFLE,
    SHUFFLE_REPEAT,
    CASINO;

    fun next(): PlaySequence {
        val all = entries
        return all[(this.ordinal + 1) % all.size]
    }
}

fun indexAfterTrackEnd(
    mode: PlaySequence,
    size: Int,
    current: Int,
    random: Random = Random,
): Int? {
    if (size <= 0) return null
    val safeCurrent = current.coerceIn(0, size - 1)
    return when (mode) {
        PlaySequence.SEQUENCE -> (safeCurrent + 1).takeIf { it in 0 until size }
        PlaySequence.REPEAT_ALL -> (safeCurrent + 1) % size
        PlaySequence.REPEAT_ONE -> safeCurrent
        PlaySequence.SHUFFLE -> (safeCurrent + 1).takeIf { it in 0 until size }
        PlaySequence.SHUFFLE_REPEAT -> (safeCurrent + 1) % size
        PlaySequence.CASINO -> {
            if (size == 1) return 0
            var pick = random.nextInt(size - 1)
            if (pick >= safeCurrent) pick += 1
            pick
        }
    }
}

fun indexForSkip(
    mode: PlaySequence,
    size: Int,
    current: Int,
    forward: Boolean,
    random: Random = Random,
): Int? {
    if (size <= 0) return null
    val safeCurrent = current.coerceIn(0, size - 1)
    return when (mode) {
        PlaySequence.SEQUENCE,
        PlaySequence.SHUFFLE,
        PlaySequence.REPEAT_ONE -> {
            val next = if (forward) safeCurrent + 1 else safeCurrent - 1
            next.takeIf { it in 0 until size }
        }
        PlaySequence.REPEAT_ALL,
        PlaySequence.SHUFFLE_REPEAT -> {
            val delta = if (forward) 1 else -1
            (((safeCurrent + delta) % size) + size) % size
        }
        PlaySequence.CASINO -> {
            if (size == 1) return 0
            var pick = random.nextInt(size - 1)
            if (pick >= safeCurrent) pick += 1
            pick
        }
    }
}