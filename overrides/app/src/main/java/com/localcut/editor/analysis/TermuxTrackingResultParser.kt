package com.localcut.editor.analysis

import com.localcut.editor.domain.animation.GlobalMotionSample
import com.localcut.editor.domain.animation.GlobalMotionStabilizationPlanner
import com.localcut.editor.domain.model.PropertyAnimation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToLong


data class TermuxGlobalMotionResult(
    val framesDecoded: Int,
    val frameRate: Double?,
    val samples: List<GlobalMotionSample>,
) {
    init {
        require(framesDecoded >= 1) { "tracking framesDecoded must be positive" }
        require(frameRate == null || (frameRate.isFinite() && frameRate > 0.0)) {
            "tracking frameRate must be finite and positive"
        }
        require(samples.isNotEmpty()) { "tracking result requires samples" }
        require(samples.zipWithNext().all { (left, right) -> right.offsetUs > left.offsetUs }) {
            "tracking samples must be strictly increasing"
        }
    }

    fun toStabilizationAnimations(
        strength: Double = 1.0,
        minConfidence: Double = 0.5,
    ): List<PropertyAnimation> = GlobalMotionStabilizationPlanner.plan(
        samples = samples,
        strength = strength,
        minConfidence = minConfidence,
    )
}

object TermuxTrackingResultParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
    }

    fun parse(responseJson: String): TermuxGlobalMotionResult {
        require(responseJson.isNotBlank()) { "tracking response must not be blank" }
        val root = json.parseToJsonElement(responseJson).jsonObject
        require(root.boolean("ok") == true) { "tracking response is not successful" }

        val job = root.objectValue("job")
        require(job.string("status") == "SUCCEEDED") { "tracking job did not succeed" }
        val output = job.objectValue("output")
        require(output.string("analysisMode") == "opencv-global-motion-v1") { "tracking analysis mode is unsupported" }
        val analysis = output.objectValue("analysis")

        val framesDecoded = analysis.int("framesDecoded")
        require(framesDecoded >= 1) { "tracking framesDecoded must be positive" }
        val frameRate = analysis.optionalDouble("frameRate")?.also { value ->
            require(value.isFinite() && value > 0.0) { "tracking frameRate must be finite and positive" }
        }

        val sampleElements = analysis["samples"]?.jsonArray
            ?: throw IllegalArgumentException("tracking samples are required")
        val samples = sampleElements.map { element -> parseSample(element, frameRate) }
        require(samples.isNotEmpty()) { "tracking result requires samples" }
        require(samples.zipWithNext().all { (left, right) -> right.offsetUs > left.offsetUs }) {
            "tracking samples must be strictly increasing"
        }

        return TermuxGlobalMotionResult(
            framesDecoded = framesDecoded,
            frameRate = frameRate,
            samples = samples,
        )
    }

    private fun parseSample(element: JsonElement, frameRate: Double?): GlobalMotionSample {
        val sample = element.jsonObject
        val frameIndex = sample.int("frameIndex")
        require(frameIndex >= 1) { "tracking frameIndex must be positive" }

        val offsetUs = sample.optionalLong("timeUs") ?: run {
            val fps = frameRate ?: throw IllegalArgumentException("tracking sample requires timeUs or frameRate")
            (frameIndex.toDouble() * 1_000_000.0 / fps).roundToLong()
        }
        require(offsetUs > 0L) { "tracking sample time must be positive" }

        val dx = sample.double("dx")
        val dy = sample.double("dy")
        val rotationDeg = sample.double("rotationDeg")
        val scale = sample.double("scale")
        val confidence = sample.double("confidence")
        require(dx.isFinite() && dy.isFinite() && rotationDeg.isFinite() && scale.isFinite() && confidence.isFinite()) {
            "tracking sample values must be finite"
        }
        require(scale > 0.0) { "tracking sample scale must be positive" }
        require(confidence in 0.0..1.0) { "tracking confidence must be in 0..1" }

        return GlobalMotionSample(
            offsetUs = offsetUs,
            dx = dx,
            dy = dy,
            rotationDeg = rotationDeg,
            scale = scale,
            confidence = confidence,
        )
    }

    private fun JsonObject.objectValue(name: String): JsonObject =
        this[name]?.jsonObject ?: throw IllegalArgumentException("tracking $name object is required")

    private fun JsonObject.string(name: String): String =
        this[name]?.jsonPrimitive?.content ?: throw IllegalArgumentException("tracking $name is required")

    private fun JsonObject.boolean(name: String): Boolean? =
        (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.int(name: String): Int =
        this[name]?.jsonPrimitive?.intOrNull ?: throw IllegalArgumentException("tracking $name must be an integer")

    private fun JsonObject.double(name: String): Double =
        this[name]?.jsonPrimitive?.doubleOrNull ?: throw IllegalArgumentException("tracking $name must be numeric")

    private fun JsonObject.optionalDouble(name: String): Double? =
        (this[name] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.optionalLong(name: String): Long? =
        (this[name] as? JsonPrimitive)?.longOrNull
}
