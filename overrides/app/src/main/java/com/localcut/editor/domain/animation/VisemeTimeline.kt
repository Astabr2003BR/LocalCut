package com.localcut.editor.domain.animation

/**
 * Hand-authored mouth shapes. This is not speech recognition: callers must
 * supply aligned cues from a user-edited timeline or a validated analyzer.
 * All values are normalized render parameters, not asset coordinates.
 */
enum class VisemeShape(
    val openness: Double,
    val widthScale: Double,
    val rounding: Double,
    val teethVisibility: Double,
) {
    REST(0.0, 1.0, 0.0, 0.0),
    M_B_P(0.0, 0.86, 0.0, 0.0),
    A(0.86, 1.02, 0.10, 0.0),
    E_I(0.44, 1.22, 0.0, 0.04),
    O(0.56, 0.78, 0.87, 0.0),
    U(0.24, 0.65, 1.0, 0.0),
    F_V(0.17, 0.96, 0.0, 0.62),
    L(0.50, 1.0, 0.04, 0.30),
    S_T(0.21, 1.02, 0.0, 0.10),
}

/** Timeline-relative half-open interval [offsetUs, endUs). */
data class VisemeCue(
    val offsetUs: Long,
    val durationUs: Long,
    val shape: VisemeShape,
    val strength: Double = 1.0,
) {
    init {
        require(offsetUs >= 0L) { "viseme offset must not be negative" }
        require(durationUs > 0L) { "viseme duration must be positive" }
        require(durationUs <= Long.MAX_VALUE - offsetUs) { "viseme time overflows" }
        require(strength.isFinite() && strength in 0.0..1.0) { "viseme strength must be finite in [0,1]" }
    }

    val endUs: Long get() = offsetUs + durationUs
}

/** Normalized mouth pose: defaults reproduce a non-speaking portrait. */
data class VisemePose(
    val openness: Double,
    val widthScale: Double,
    val rounding: Double,
    val teethVisibility: Double,
) {
    companion object {
        val REST = VisemePose(0.0, 1.0, 0.0, 0.0)
    }
}

/**
 * Immutable, strictly ordered timeline for future sprite/mesh rendering.
 * Never edits a Project or changes an existing TALK/blink effect.
 */
class VisemeTimeline(cues: List<VisemeCue>, val durationUs: Long) {
    private val entries = cues.toList()

    init {
        require(durationUs >= 0L) { "viseme timeline duration must not be negative" }
        var previousEndUs = 0L
        entries.forEachIndexed { index, cue ->
            require(cue.endUs <= durationUs) { "viseme exceeds timeline duration" }
            require(index == 0 || cue.offsetUs >= previousEndUs) {
                "visemes must be sorted and non-overlapping"
            }
            previousEndUs = cue.endUs
        }
    }

    /** O(log n) cue lookup; silence and the exact end time return REST. */
    fun poseAt(timeUs: Long): VisemePose {
        require(timeUs >= 0L) { "viseme sample time must not be negative" }
        if (timeUs >= durationUs) return VisemePose.REST
        var low = 0
        var high = entries.size - 1
        while (low <= high) {
            val middle = low + (high - low) / 2
            val cue = entries[middle]
            when {
                timeUs < cue.offsetUs -> high = middle - 1
                timeUs >= cue.endUs -> low = middle + 1
                else -> return poseInside(middle, timeUs)
            }
        }
        return VisemePose.REST
    }

    /**
     * Blend directly between adjacent visemes instead of closing the mouth
     * at every syllable boundary. Silence before/after a run still returns REST.
     */
    private fun poseInside(index: Int, timeUs: Long): VisemePose {
        val cue = entries[index]
        val target = poseFor(cue)
        val previous = entries.getOrNull(index - 1)
        val next = entries.getOrNull(index + 1)
        val joinsPrevious = previous?.endUs == cue.offsetUs
        val joinsNext = next?.offsetUs == cue.endUs
        val from = if (joinsPrevious && previous != null) poseFor(previous) else VisemePose.REST

        val attackUs = minOf(40_000L, cue.durationUs / 3L)
        val releaseUs = if (joinsNext) 0L else minOf(50_000L, cue.durationUs / 3L)
        if (attackUs > 0L && timeUs < cue.offsetUs + attackUs) {
            val fraction = (timeUs - cue.offsetUs).toDouble() / attackUs.toDouble()
            return interpolate(from, target, smoothstep(fraction))
        }
        if (releaseUs > 0L && timeUs >= cue.endUs - releaseUs) {
            val fraction = (timeUs - (cue.endUs - releaseUs)).toDouble() / releaseUs.toDouble()
            return interpolate(target, VisemePose.REST, smoothstep(fraction))
        }
        return target
    }

    private fun poseFor(cue: VisemeCue): VisemePose {
        val shape = cue.shape
        val weight = cue.strength
        return VisemePose(
            openness = shape.openness * weight,
            widthScale = 1.0 + (shape.widthScale - 1.0) * weight,
            rounding = shape.rounding * weight,
            teethVisibility = shape.teethVisibility * weight,
        )
    }

    private fun interpolate(from: VisemePose, to: VisemePose, fraction: Double): VisemePose {
        val t = fraction.coerceIn(0.0, 1.0)
        fun lerp(a: Double, b: Double): Double = a + (b - a) * t
        return VisemePose(
            openness = lerp(from.openness, to.openness),
            widthScale = lerp(from.widthScale, to.widthScale),
            rounding = lerp(from.rounding, to.rounding),
            teethVisibility = lerp(from.teethVisibility, to.teethVisibility),
        )
    }

    private fun smoothstep(value: Double): Double {
        val t = value.coerceIn(0.0, 1.0)
        return t * t * (3.0 - 2.0 * t)
    }
}
