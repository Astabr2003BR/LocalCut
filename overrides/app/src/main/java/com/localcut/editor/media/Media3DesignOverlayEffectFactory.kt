package com.localcut.editor.media

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.RectF
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.OverlayEffect
import com.localcut.editor.domain.model.Project
import com.localcut.editor.domain.model.ShapeKind
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
object Media3DesignOverlayEffectFactory {
    fun create(
        project: Project,
        outputWidth: Int,
        outputHeight: Int,
    ): OverlayEffect? {
        val specs = DesignOverlayRenderSpecCompiler.compile(project, outputWidth, outputHeight)
        if (specs.isEmpty()) return null
        return OverlayEffect(listOf(DesignCanvasOverlay(specs, outputWidth, outputHeight)))
    }

    private class DesignCanvasOverlay(
        private val specs: List<DesignOverlayRenderSpec>,
        outputWidth: Int,
        outputHeight: Int,
    ) : CanvasOverlay(false) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setCanvasSize(outputWidth, outputHeight)
        }

        override fun onDraw(canvas: Canvas, presentationTimeUs: Long) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            specs.asSequence()
                .filter { it.isActiveAt(presentationTimeUs) }
                .forEach { drawSpec(canvas, it) }
        }

        private fun drawSpec(canvas: Canvas, spec: DesignOverlayRenderSpec) {
            val save = canvas.save()
            try {
                canvas.translate(spec.centerX, spec.centerY)
                canvas.rotate(spec.rotationDegrees)
                when (spec.shape.kind) {
                    ShapeKind.RECTANGLE -> drawRectangle(canvas, spec)
                    ShapeKind.ELLIPSE -> drawEllipse(canvas, spec)
                    ShapeKind.LINE -> drawLine(canvas, spec)
                    ShapeKind.PATH -> drawPath(canvas, spec)
                }
            } finally {
                canvas.restoreToCount(save)
            }
        }

        private fun drawRectangle(canvas: Canvas, spec: DesignOverlayRenderSpec) {
            val rect = localRect(spec)
            spec.shape.fillArgb?.let { color ->
                configureFill(color, spec.alpha)
                canvas.drawRoundRect(rect, spec.cornerRadiusPx, spec.cornerRadiusPx, paint)
            }
            drawStroke(canvas, spec) { canvas.drawRoundRect(rect, spec.cornerRadiusPx, spec.cornerRadiusPx, paint) }
        }

        private fun drawEllipse(canvas: Canvas, spec: DesignOverlayRenderSpec) {
            val rect = localRect(spec)
            spec.shape.fillArgb?.let { color ->
                configureFill(color, spec.alpha)
                canvas.drawOval(rect, paint)
            }
            drawStroke(canvas, spec) { canvas.drawOval(rect, paint) }
        }

        private fun drawLine(canvas: Canvas, spec: DesignOverlayRenderSpec) {
            val color = spec.shape.strokeArgb ?: spec.shape.fillArgb ?: Color.WHITE
            configureStroke(color, spec.alpha, max(spec.strokeWidthPx, DEFAULT_LINE_STROKE_PX))
            canvas.drawLine(-spec.widthPx / 2f, 0f, spec.widthPx / 2f, 0f, paint)
        }

        private fun drawPath(canvas: Canvas, spec: DesignOverlayRenderSpec) {
            val vector = spec.shape.path ?: return
            if (vector.points.isEmpty()) return
            val path = Path()
            vector.points.forEachIndexed { index, point ->
                val x = ((point.x - 0.5) * spec.widthPx).toFloat()
                val y = ((point.y - 0.5) * spec.heightPx).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            if (vector.closed) path.close()
            spec.shape.fillArgb?.takeIf { vector.closed }?.let { color ->
                configureFill(color, spec.alpha)
                canvas.drawPath(path, paint)
            }
            val strokeColor = spec.shape.strokeArgb ?: if (!vector.closed) spec.shape.fillArgb else null
            if (strokeColor != null) {
                configureStroke(strokeColor, spec.alpha, max(spec.strokeWidthPx, DEFAULT_LINE_STROKE_PX))
                canvas.drawPath(path, paint)
            }
        }

        private fun drawStroke(
            canvas: Canvas,
            spec: DesignOverlayRenderSpec,
            draw: () -> Unit,
        ) {
            val color = spec.shape.strokeArgb ?: return
            if (spec.strokeWidthPx <= 0f) return
            configureStroke(color, spec.alpha, spec.strokeWidthPx)
            draw()
        }

        private fun configureFill(argb: Int, alpha: Float) {
            paint.style = Paint.Style.FILL
            paint.color = applyAlpha(argb, alpha)
        }

        private fun configureStroke(argb: Int, alpha: Float, widthPx: Float) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = widthPx
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = applyAlpha(argb, alpha)
        }

        private fun localRect(spec: DesignOverlayRenderSpec): RectF = RectF(
            -spec.widthPx / 2f,
            -spec.heightPx / 2f,
            spec.widthPx / 2f,
            spec.heightPx / 2f,
        )

        private fun applyAlpha(argb: Int, alpha: Float): Int {
            val sourceAlpha = (argb ushr 24) and 0xFF
            val combined = (sourceAlpha * alpha.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
            return (combined shl 24) or (argb and 0x00FFFFFF)
        }
    }

    private const val DEFAULT_LINE_STROKE_PX = 4f
}
