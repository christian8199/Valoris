package com.example.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.example.ui.components.downsampleDoubles

object ValorisWidgetChartRenderer {
    private const val WIDTH_PX = 460
    private const val HEIGHT_PX = 240

    fun renderBackgroundChart(
        points: List<Double>,
        isPositive: Boolean,
        isNegative: Boolean,
        showZeroLine: Boolean
    ): Bitmap {
        // RGB_565 keeps bitmap memory tiny (~220 KB) while delivering crisp gradient & anti-aliased chart lines
        val bitmap = Bitmap.createBitmap(WIDTH_PX, HEIGHT_PX, Bitmap.Config.RGB_565)
        val canvas = Canvas(bitmap)
        val w = WIDTH_PX.toFloat()
        val h = HEIGHT_PX.toFloat()

        // 1. Forest Obsidian editorial card gradient background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                h,
                w,
                0f,
                Color.parseColor("#0E1E19"),
                Color.parseColor("#1D3930"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w, h, bgPaint)

        // Subtle decorative atmospheric glow in top-right corner
        val accentColor = when {
            isPositive -> Color.parseColor("#3CCB88")
            isNegative -> Color.parseColor("#F06E6E")
            else -> Color.parseColor("#D4B259")
        }

        val sampled = downsampleDoubles(points, maxPoints = 72)
        if (sampled.size >= 2) {
            val minVal = sampled.minOrNull() ?: 0.0
            val maxVal = sampled.maxOrNull() ?: 0.0
            val rawRange = maxVal - minVal
            val paddingVal = if (rawRange < 1e-6) {
                (kotlin.math.abs(maxVal) * 0.05).coerceAtLeast(1.0)
            } else {
                rawRange * 0.14
            }
            val effectiveMin = minVal - paddingVal
            val effectiveMax = maxVal + paddingVal
            val span = (effectiveMax - effectiveMin).coerceAtLeast(1e-6)

            val chartTop = h * 0.16f
            val chartBottom = h * 0.86f
            val chartHeight = (chartBottom - chartTop).coerceAtLeast(1f)
            val leftEdge = 0f
            val rightEdge = w - 6f

            fun xAt(index: Int): Float {
                val frac = index.toFloat() / (sampled.size - 1).coerceAtLeast(1)
                return leftEdge + frac * (rightEdge - leftEdge)
            }

            fun yAt(value: Double): Float {
                val norm = ((value - effectiveMin) / span).toFloat().coerceIn(0f, 1f)
                return chartBottom - norm * chartHeight
            }

            // Optional zero baseline for Total Return
            if (showZeroLine && 0.0 in effectiveMin..effectiveMax) {
                val zeroY = yAt(0.0)
                val zeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(72, 246, 243, 235)
                    strokeWidth = 1.6f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
                }
                canvas.drawLine(0f, zeroY, w, zeroY, zeroPaint)
            } else {
                // Subtle mid-level reference rule
                val midY = chartTop + chartHeight * 0.5f
                val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(30, 246, 243, 235)
                    strokeWidth = 1.2f
                    style = Paint.Style.STROKE
                    pathEffect = DashPathEffect(floatArrayOf(6f, 10f), 0f)
                }
                canvas.drawLine(0f, midY, w, midY, gridPaint)
            }

            // Build smooth curve path
            val linePath = Path()
            val fillPath = Path()

            val coords = sampled.mapIndexed { idx, v -> xAt(idx) to yAt(v) }
            linePath.moveTo(coords.first().first, coords.first().second)
            fillPath.moveTo(coords.first().first, h)
            fillPath.lineTo(coords.first().first, coords.first().second)

            for (i in 1 until coords.size) {
                val prev = coords[i - 1]
                val curr = coords[i]
                val midX = (prev.first + curr.first) * 0.5f
                linePath.cubicTo(midX, prev.second, midX, curr.second, curr.first, curr.second)
                fillPath.cubicTo(midX, prev.second, midX, curr.second, curr.first, curr.second)
            }
            fillPath.lineTo(w, coords.last().second)
            fillPath.lineTo(w, h)
            fillPath.close()

            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = LinearGradient(
                    0f,
                    chartTop,
                    0f,
                    h,
                    Color.argb(
                        92,
                        Color.red(accentColor),
                        Color.green(accentColor),
                        Color.blue(accentColor)
                    ),
                    Color.argb(
                        8,
                        Color.red(accentColor),
                        Color.green(accentColor),
                        Color.blue(accentColor)
                    ),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawPath(fillPath, fillPaint)

            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = 4.2f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            canvas.drawPath(linePath, strokePaint)

            // Endpoint halo & dot
            val endPt = coords.last()
            val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(
                    85,
                    Color.red(accentColor),
                    Color.green(accentColor),
                    Color.blue(accentColor)
                )
                style = Paint.Style.FILL
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.FILL
            }
            canvas.drawCircle(endPt.first, endPt.second, 9f, haloPaint)
            canvas.drawCircle(endPt.first, endPt.second, 4.5f, dotPaint)
        } else {
            // Clean subtle flat horizon line for empty/single-point state
            val baselineY = h * 0.62f
            val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(65, 212, 178, 89)
                strokeWidth = 2.5f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
            }
            canvas.drawLine(0f, baselineY, w, baselineY, basePaint)
        }

        // Bottom readability scrim so overlaid hero figures remain crisp at any graph height
        val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                h * 0.42f,
                0f,
                h,
                Color.argb(0, 12, 26, 21),
                Color.argb(185, 11, 24, 19),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, h * 0.42f, w, h, scrimPaint)

        return bitmap
    }
}
