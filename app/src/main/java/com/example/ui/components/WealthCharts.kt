package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.model.AllocationSlice
import com.example.domain.model.AssetClass
import com.example.domain.model.AssetMovementSeries
import com.example.domain.model.HistoricalPoint
import com.example.ui.theme.LocalWealthColors
import com.example.ui.theme.NumericTextStyles
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AllocationDonutChart(
    slices: List<AllocationSlice>,
    totalLabel: String,
    modifier: Modifier = Modifier,
    chartSize: Dp = 148.dp,
    strokeWidth: Dp = 18.dp
) {
    val wealthColors = LocalWealthColors.current
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val sweepScale by animateFloatAsState(
        targetValue = if (slices.isEmpty()) 0f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "donut_sweep"
    )
    val sliceSpecs = remember(slices, wealthColors) {
        val gapDegrees = if (slices.size > 1) 3.5f else 0f
        var startAngle = -90f
        slices.map { slice ->
            val fullSweep = ((slice.percentage / 100.0) * 360.0).toFloat()
            val arcStart = startAngle + gapDegrees / 2f
            val baseSweep = fullSweep - gapDegrees
            startAngle += fullSweep
            Triple(colorForAssetClass(slice.assetClass, wealthColors), arcStart, baseSweep)
        }
    }

    Box(
        modifier = modifier.size(chartSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    val strokePx = strokeWidth.toPx()
                    val inset = strokePx / 2f
                    val arcSize = Size(size.width - strokePx, size.height - strokePx)
                    val topLeft = Offset(inset, inset)
                    val trackStyle = Stroke(width = strokePx, cap = StrokeCap.Round)
                    val sliceStyle = Stroke(width = strokePx, cap = StrokeCap.Butt)

                    onDrawBehind {
                        drawArc(
                            color = trackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = trackStyle
                        )

                        for (i in sliceSpecs.indices) {
                            val (sliceColor, arcStart, baseSweep) = sliceSpecs[i]
                            drawArc(
                                color = sliceColor,
                                startAngle = arcStart,
                                sweepAngle = (baseSweep * sweepScale).coerceAtLeast(1.5f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = sliceStyle
                            )
                        }
                    }
                }
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${slices.size}",
                style = NumericTextStyles.cardAmount,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun downsampleDoubles(points: List<Double>, maxPoints: Int = 56): List<Double> {
    if (points.size <= maxPoints || maxPoints < 2) return points
    val result = ArrayList<Double>(maxPoints)
    val lastIdx = points.lastIndex
    val step = lastIdx.toDouble() / (maxPoints - 1).toDouble()
    for (i in 0 until maxPoints - 1) {
        result.add(points[(i * step).toInt().coerceIn(0, lastIdx)])
    }
    result.add(points[lastIdx])
    return result
}

fun computeNormalizedRangePercent(
    startNormalizedPercent: Double,
    endNormalizedPercent: Double
): Double {
    val startFactor = 1.0 + (startNormalizedPercent / 100.0)
    val endFactor = 1.0 + (endNormalizedPercent / 100.0)
    return if (startFactor > 1e-6) {
        ((endFactor - startFactor) / startFactor) * 100.0
    } else {
        endNormalizedPercent - startNormalizedPercent
    }
}

@Composable
fun TrendAreaChart(
    points: List<Double>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    strokeWidthDp: Dp = 2.5.dp,
    showEndDot: Boolean = true
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val sampledPoints = remember(points) { downsampleDoubles(points, maxPoints = 56) }

    Box(
        modifier = modifier
            .graphicsLayer()
            .drawWithCache {
            if (sampledPoints.size < 2) {
                val y = size.height * 0.55f
                val strokePx = strokeWidthDp.toPx()
                return@drawWithCache onDrawBehind {
                    drawLine(
                        color = lineColor.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }

            var min = Double.MAX_VALUE
            var max = -Double.MAX_VALUE
            for (i in sampledPoints.indices) {
                val v = sampledPoints[i]
                if (v < min) min = v
                if (v > max) max = v
            }
            val range = (max - min).takeIf { it > 1e-9 } ?: (abs(max).coerceAtLeast(1.0) * 0.05)

            val padTop = 8.dp.toPx()
            val padBottom = 8.dp.toPx()
            val usableHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
            val gridStroke = 0.8.dp.toPx()
            val gridY1 = padTop + usableHeight * 0.25f
            val gridY2 = padTop + usableHeight * 0.50f
            val gridY3 = padTop + usableHeight * 0.75f

            val n = sampledPoints.size
            val denom = (n - 1).toFloat()
            val xs = FloatArray(n)
            val ys = FloatArray(n)
            for (i in 0 until n) {
                xs[i] = (i.toFloat() / denom) * size.width
                val normY = ((sampledPoints[i] - min) / range).toFloat().coerceIn(0f, 1f)
                ys[i] = padTop + usableHeight * (1f - normY)
            }

            val linePath = buildSmoothPath(xs, ys, n)
            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(xs[n - 1], size.height)
                lineTo(xs[0], size.height)
                close()
            }
            val fillBrush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.26f),
                    lineColor.copy(alpha = 0.0f)
                ),
                startY = padTop,
                endY = size.height
            )
            val lineStroke = Stroke(
                width = strokeWidthDp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Bevel
            )
            val endDot = Offset(xs[n - 1], ys[n - 1])
            val outerRadius = 7.dp.toPx()
            val innerRadius = 3.5.dp.toPx()
            val outerDotColor = lineColor.copy(alpha = 0.22f)

            onDrawBehind {
                drawLine(color = gridColor, start = Offset(0f, gridY1), end = Offset(size.width, gridY1), strokeWidth = gridStroke)
                drawLine(color = gridColor, start = Offset(0f, gridY2), end = Offset(size.width, gridY2), strokeWidth = gridStroke)
                drawLine(color = gridColor, start = Offset(0f, gridY3), end = Offset(size.width, gridY3), strokeWidth = gridStroke)
                drawPath(path = fillPath, brush = fillBrush)
                drawPath(path = linePath, color = lineColor, style = lineStroke)
                if (showEndDot) {
                    drawCircle(color = outerDotColor, radius = outerRadius, center = endDot)
                    drawCircle(color = lineColor, radius = innerRadius, center = endDot)
                }
            }
        }
    )
}

@Composable
fun InteractiveTotalMovementChart(
    points: List<HistoricalPoint>,
    lineColor: Color,
    scrubIndex: Int?,
    onScrubIndexChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    showZeroBaseline: Boolean = false,
    rangeSelection: IntRange? = null,
    onRangeSelectionChange: ((IntRange?) -> Unit)? = null
) {
    val wealthColors = LocalWealthColors.current
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val zeroLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)

    val (minVal, maxVal, rangeVal) = remember(points, showZeroBaseline) {
        if (points.size < 2) {
            Triple(0.0, 0.0, 1.0)
        } else {
            var min = if (showZeroBaseline) 0.0 else Double.MAX_VALUE
            var max = if (showZeroBaseline) 0.0 else -Double.MAX_VALUE
            for (i in points.indices) {
                val v = points[i].value
                if (v < min) min = v
                if (v > max) max = v
            }
            val range = (max - min).takeIf { it > 1e-9 } ?: (abs(max).coerceAtLeast(1.0) * 0.05)
            Triple(min, max, range)
        }
    }

    Box(
        modifier = modifier.chartScrubGestures(
            pointCount = points.size,
            onScrubIndexChange = onScrubIndexChange,
            onRangeSelectionChange = onRangeSelectionChange
        )
    ) {
        // Cached curve layer (re-calculated only when points/color/size change)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    if (points.size < 2) {
                        val y = size.height * 0.55f
                        val strokePx = 2.5.dp.toPx()
                        return@drawWithCache onDrawBehind {
                            drawLine(
                                color = lineColor.copy(alpha = 0.5f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = strokePx,
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    val padTop = 12.dp.toPx()
                    val padBottom = 12.dp.toPx()
                    val usableHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                    val gridStroke = 0.8.dp.toPx()
                    val gridY1 = padTop + usableHeight * 0.2f
                    val gridY2 = padTop + usableHeight * 0.5f
                    val gridY3 = padTop + usableHeight * 0.8f
                    val zeroY = if (showZeroBaseline && minVal <= 0.0 && maxVal >= 0.0) {
                        val zeroNorm = ((0.0 - minVal) / rangeVal).toFloat().coerceIn(0f, 1f)
                        padTop + usableHeight * (1f - zeroNorm)
                    } else null
                    val zeroDash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    val zeroStroke = 1.dp.toPx()

                    val n = points.size
                    val denom = (n - 1).toFloat()
                    val xs = FloatArray(n)
                    val ys = FloatArray(n)
                    for (i in 0 until n) {
                        xs[i] = (i.toFloat() / denom) * size.width
                        val normY = ((points[i].value - minVal) / rangeVal).toFloat().coerceIn(0f, 1f)
                        ys[i] = padTop + usableHeight * (1f - normY)
                    }

                    val linePath = buildSmoothPath(xs, ys, n)
                    val fillBaseY = zeroY ?: size.height
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(xs[n - 1], fillBaseY)
                        lineTo(xs[0], fillBaseY)
                        close()
                    }
                    val fillBrush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.28f),
                            lineColor.copy(alpha = 0.02f)
                        ),
                        startY = padTop,
                        endY = size.height
                    )
                    val lineStroke = Stroke(
                        width = 2.75.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Bevel
                    )

                    onDrawBehind {
                        drawLine(color = gridColor, start = Offset(0f, gridY1), end = Offset(size.width, gridY1), strokeWidth = gridStroke)
                        drawLine(color = gridColor, start = Offset(0f, gridY2), end = Offset(size.width, gridY2), strokeWidth = gridStroke)
                        drawLine(color = gridColor, start = Offset(0f, gridY3), end = Offset(size.width, gridY3), strokeWidth = gridStroke)
                        if (zeroY != null) {
                            drawLine(
                                color = zeroLineColor,
                                start = Offset(0f, zeroY),
                                end = Offset(size.width, zeroY),
                                strokeWidth = zeroStroke,
                                pathEffect = zeroDash
                            )
                        }
                        drawPath(path = fillPath, brush = fillBrush)
                        drawPath(path = linePath, color = lineColor, style = lineStroke)
                    }
                }
        )

        // Lightweight scrub crosshair / two-finger range overlay
        if (points.size >= 2) {
            val latestRangeSelection by rememberUpdatedState(rangeSelection)
            val latestScrubIndex by rememberUpdatedState(scrubIndex)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        val rangePath = Path()
                        val padTop = 12.dp.toPx()
                        val padBottom = 12.dp.toPx()
                        val usableHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                        val denom = (points.size - 1).toFloat()
                        val rangeLineStroke = Stroke(
                            width = 3.25.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Bevel
                        )
                        val markerStroke = 1.25.dp.toPx()
                        val crosshairStroke = 1.dp.toPx()
                        val outerR = 8.dp.toPx()
                        val innerR = 4.25.dp.toPx()
                        val dotInnerR = 4.dp.toPx()

                        onDrawBehind {
                            val activeRange = latestRangeSelection
                            val activeScrub = latestScrubIndex
                            if (activeRange != null) {
                                val rawMinIdx = minOf(activeRange.first, activeRange.last).coerceIn(0, points.lastIndex)
                                val rawMaxIdx = maxOf(activeRange.first, activeRange.last).coerceIn(0, points.lastIndex)
                                val startX = (rawMinIdx.toFloat() / denom) * size.width
                                val endX = (rawMaxIdx.toFloat() / denom) * size.width
                                val startNormY = ((points[rawMinIdx].value - minVal) / rangeVal).toFloat().coerceIn(0f, 1f)
                                val endNormY = ((points[rawMaxIdx].value - minVal) / rangeVal).toFloat().coerceIn(0f, 1f)
                                val startY = padTop + usableHeight * (1f - startNormY)
                                val endY = padTop + usableHeight * (1f - endNormY)
                                val rangeAccent = if (points[rawMaxIdx].value >= points[rawMinIdx].value) {
                                    wealthColors.gain
                                } else {
                                    wealthColors.loss
                                }

                                if (endX > startX) {
                                    drawRect(
                                        color = rangeAccent.copy(alpha = 0.14f),
                                        topLeft = Offset(startX, 0f),
                                        size = Size(endX - startX, size.height)
                                    )
                                }

                                if (rawMaxIdx > rawMinIdx) {
                                    rangePath.reset()
                                    rangePath.moveTo(startX, startY)
                                    var prevX = startX
                                    var prevY = startY
                                    for (i in (rawMinIdx + 1)..rawMaxIdx) {
                                        val currX = (i.toFloat() / denom) * size.width
                                        val currNormY = ((points[i].value - minVal) / rangeVal).toFloat().coerceIn(0f, 1f)
                                        val currY = padTop + usableHeight * (1f - currNormY)
                                        val midX = (prevX + currX) * 0.5f
                                        rangePath.cubicTo(midX, prevY, midX, currY, currX, currY)
                                        prevX = currX
                                        prevY = currY
                                    }
                                    drawPath(
                                        path = rangePath,
                                        color = rangeAccent,
                                        style = rangeLineStroke
                                    )
                                }

                                val markerColor = rangeAccent.copy(alpha = 0.8f)
                                drawLine(
                                    color = markerColor,
                                    start = Offset(startX, 0f),
                                    end = Offset(startX, size.height),
                                    strokeWidth = markerStroke,
                                    pathEffect = dashEffect
                                )
                                if (rawMaxIdx != rawMinIdx) {
                                    drawLine(
                                        color = markerColor,
                                        start = Offset(endX, 0f),
                                        end = Offset(endX, size.height),
                                        strokeWidth = markerStroke,
                                        pathEffect = dashEffect
                                    )
                                }

                                val startOffset = Offset(startX, startY)
                                drawCircle(color = rangeAccent.copy(alpha = 0.24f), radius = outerR, center = startOffset)
                                drawCircle(color = rangeAccent, radius = innerR, center = startOffset)
                                if (rawMaxIdx != rawMinIdx) {
                                    val endOffset = Offset(endX, endY)
                                    drawCircle(color = rangeAccent.copy(alpha = 0.24f), radius = outerR, center = endOffset)
                                    drawCircle(color = rangeAccent, radius = innerR, center = endOffset)
                                }
                            } else {
                                val highlightIdx = activeScrub?.coerceIn(0, points.lastIndex) ?: points.lastIndex
                                val x = (highlightIdx.toFloat() / denom) * size.width
                                val normY = ((points[highlightIdx].value - minVal) / rangeVal).toFloat().coerceIn(0f, 1f)
                                val y = padTop + usableHeight * (1f - normY)
                                val highlightOffset = Offset(x, y)

                                if (activeScrub != null) {
                                    drawLine(
                                        color = crosshairColor,
                                        start = Offset(x, 0f),
                                        end = Offset(x, size.height),
                                        strokeWidth = crosshairStroke,
                                        pathEffect = dashEffect
                                    )
                                }

                                drawCircle(
                                    color = lineColor.copy(alpha = 0.24f),
                                    radius = outerR,
                                    center = highlightOffset
                                )
                                drawCircle(
                                    color = lineColor,
                                    radius = dotInnerR,
                                    center = highlightOffset
                                )
                            }
                        }
                    }
            )
        }
    }
}

@Composable
fun MultiAssetComparisonChart(
    seriesList: List<AssetMovementSeries>,
    seriesColors: Map<String, Color>,
    scrubIndex: Int?,
    onScrubIndexChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    rangeSelection: IntRange? = null,
    onRangeSelectionChange: ((IntRange?) -> Unit)? = null
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val zeroLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val crosshairColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
    val rangeBandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
    val rangeBoundaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
    val pointCount = seriesList.maxOfOrNull { it.normalizedPercentPoints.size } ?: 0

    val (rawMin, range) = remember(seriesList) {
        var min = 0.0
        var max = 0.0
        for (s in seriesList) {
            val pts = s.normalizedPercentPoints
            for (i in pts.indices) {
                val v = pts[i].value
                if (v < min) min = v
                if (v > max) max = v
            }
        }
        min to ((max - min).takeIf { it > 1e-6 } ?: 2.0)
    }

    Box(
        modifier = modifier.chartScrubGestures(
            pointCount = pointCount,
            onScrubIndexChange = onScrubIndexChange,
            onRangeSelectionChange = onRangeSelectionChange
        )
    ) {
        // Cached multi-series paths layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawWithCache {
                    if (seriesList.isEmpty() || pointCount < 2) {
                        return@drawWithCache onDrawBehind {}
                    }

                    val padTop = 14.dp.toPx()
                    val padBottom = 14.dp.toPx()
                    val usableHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                    val gridStroke = 0.8.dp.toPx()
                    val gridY1 = padTop + usableHeight * 0.2f
                    val gridY2 = padTop + usableHeight * 0.8f
                    val zeroNorm = ((0.0 - rawMin) / range).toFloat().coerceIn(0f, 1f)
                    val zeroY = padTop + usableHeight * (1f - zeroNorm)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    val zeroStroke = 1.dp.toPx()
                    val lineStroke = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Bevel
                    )

                    val renderedSeries = seriesList.mapNotNull { series ->
                        val pts = series.normalizedPercentPoints
                        val n = pts.size
                        if (n < 2) return@mapNotNull null
                        val color = seriesColors[series.symbol] ?: Color.Gray
                        val denom = (n - 1).toFloat()
                        val xs = FloatArray(n)
                        val ys = FloatArray(n)
                        for (i in 0 until n) {
                            xs[i] = (i.toFloat() / denom) * size.width
                            val normY = ((pts[i].value - rawMin) / range).toFloat().coerceIn(0f, 1f)
                            ys[i] = padTop + usableHeight * (1f - normY)
                        }
                        color to buildSmoothPath(xs, ys, n)
                    }

                    onDrawBehind {
                        drawLine(color = gridColor, start = Offset(0f, gridY1), end = Offset(size.width, gridY1), strokeWidth = gridStroke)
                        drawLine(color = gridColor, start = Offset(0f, gridY2), end = Offset(size.width, gridY2), strokeWidth = gridStroke)
                        drawLine(
                            color = zeroLineColor,
                            start = Offset(0f, zeroY),
                            end = Offset(size.width, zeroY),
                            strokeWidth = zeroStroke,
                            pathEffect = dashEffect
                        )
                        renderedSeries.forEach { (color, path) ->
                            drawPath(path = path, color = color, style = lineStroke)
                        }
                    }
                }
        )

        // Lightweight scrub crosshair / two-finger range overlay
        if (seriesList.isNotEmpty() && pointCount >= 2) {
            val latestRangeSelection by rememberUpdatedState(rangeSelection)
            val latestScrubIndex by rememberUpdatedState(scrubIndex)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        val padTop = 14.dp.toPx()
                        val padBottom = 14.dp.toPx()
                        val usableHeight = (size.height - padTop - padBottom).coerceAtLeast(1f)
                        val markerStroke = 1.25.dp.toPx()
                        val crosshairStroke = 1.dp.toPx()
                        val rangeDotRadius = 4.25.dp.toPx()
                        val rangeHaloRadius = 7.5.dp.toPx()
                        val activeDotRadius = 4.dp.toPx()
                        val idleDotRadius = 3.dp.toPx()

                        onDrawBehind {
                            val activeRange = latestRangeSelection
                            val activeScrub = latestScrubIndex
                            if (activeRange != null) {
                                val minIdx = minOf(activeRange.first, activeRange.last).coerceIn(0, pointCount - 1)
                                val maxIdx = maxOf(activeRange.first, activeRange.last).coerceIn(0, pointCount - 1)
                                val denom = (pointCount - 1).toFloat()
                                val startX = (minIdx.toFloat() / denom) * size.width
                                val endX = (maxIdx.toFloat() / denom) * size.width

                                if (endX > startX) {
                                    drawRect(
                                        color = rangeBandColor,
                                        topLeft = Offset(startX, 0f),
                                        size = Size(endX - startX, size.height)
                                    )
                                }

                                drawLine(
                                    color = rangeBoundaryColor,
                                    start = Offset(startX, 0f),
                                    end = Offset(startX, size.height),
                                    strokeWidth = markerStroke,
                                    pathEffect = dashEffect
                                )
                                if (maxIdx != minIdx) {
                                    drawLine(
                                        color = rangeBoundaryColor,
                                        start = Offset(endX, 0f),
                                        end = Offset(endX, size.height),
                                        strokeWidth = markerStroke,
                                        pathEffect = dashEffect
                                    )
                                }

                                for (i in seriesList.indices) {
                                    val series = seriesList[i]
                                    val pts = series.normalizedPercentPoints
                                    if (pts.size < 2) continue
                                    val color = seriesColors[series.symbol] ?: Color.Gray
                                    val sDenom = (pts.size - 1).toFloat()
                                    val sMinIdx = minIdx.coerceIn(0, pts.lastIndex)
                                    val sx = (sMinIdx.toFloat() / sDenom) * size.width
                                    val sNormY = ((pts[sMinIdx].value - rawMin) / range).toFloat().coerceIn(0f, 1f)
                                    val sy = padTop + usableHeight * (1f - sNormY)
                                    val sCenter = Offset(sx, sy)
                                    drawCircle(color = color.copy(alpha = 0.24f), radius = rangeHaloRadius, center = sCenter)
                                    drawCircle(color = color, radius = rangeDotRadius, center = sCenter)

                                    if (maxIdx != minIdx) {
                                        val sMaxIdx = maxIdx.coerceIn(0, pts.lastIndex)
                                        val ex = (sMaxIdx.toFloat() / sDenom) * size.width
                                        val eNormY = ((pts[sMaxIdx].value - rawMin) / range).toFloat().coerceIn(0f, 1f)
                                        val ey = padTop + usableHeight * (1f - eNormY)
                                        val eCenter = Offset(ex, ey)
                                        drawCircle(color = color.copy(alpha = 0.24f), radius = rangeHaloRadius, center = eCenter)
                                        drawCircle(color = color, radius = rangeDotRadius, center = eCenter)
                                    }
                                }
                            } else {
                                if (activeScrub != null) {
                                    val scrubX = (activeScrub.coerceIn(0, pointCount - 1).toFloat() / (pointCount - 1).toFloat()) * size.width
                                    drawLine(
                                        color = crosshairColor,
                                        start = Offset(scrubX, 0f),
                                        end = Offset(scrubX, size.height),
                                        strokeWidth = crosshairStroke,
                                        pathEffect = dashEffect
                                    )
                                }

                                val dotRadius = if (activeScrub != null) activeDotRadius else idleDotRadius
                                for (i in seriesList.indices) {
                                    val series = seriesList[i]
                                    val pts = series.normalizedPercentPoints
                                    if (pts.size < 2) continue
                                    val color = seriesColors[series.symbol] ?: Color.Gray
                                    val dotIdx = activeScrub?.coerceIn(0, pts.lastIndex) ?: pts.lastIndex
                                    val x = (dotIdx.toFloat() / (pts.size - 1).toFloat()) * size.width
                                    val normY = ((pts[dotIdx].value - rawMin) / range).toFloat().coerceIn(0f, 1f)
                                    val y = padTop + usableHeight * (1f - normY)
                                    drawCircle(color = color, radius = dotRadius, center = Offset(x, y))
                                }
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun Modifier.chartScrubGestures(
    pointCount: Int,
    onScrubIndexChange: (Int?) -> Unit,
    onRangeSelectionChange: ((IntRange?) -> Unit)? = null
): Modifier {
    val currentScrubCallback by rememberUpdatedState(onScrubIndexChange)
    val currentRangeCallback by rememberUpdatedState(onRangeSelectionChange)

    return pointerInput(pointCount, onRangeSelectionChange != null) {
        if (pointCount < 2) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val touchSlop = viewConfiguration.touchSlop
            var dragLocked = false
            var rangeLocked = false
            var totalDx = 0f
            var totalDy = 0f
            var lastScrub: Int? = null
            var lastRange: IntRange? = null

            fun indexForX(x: Float): Int {
                val w = size.width.toFloat().coerceAtLeast(1f)
                return ((x / w) * (pointCount - 1))
                    .roundToInt()
                    .coerceIn(0, pointCount - 1)
            }

            fun emitScrub(value: Int?) {
                if (lastScrub != value) {
                    lastScrub = value
                    currentScrubCallback(value)
                }
            }

            fun emitRange(value: IntRange?) {
                if (lastRange != value) {
                    lastRange = value
                    currentRangeCallback?.invoke(value)
                }
            }

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }

                    if (pressed.isEmpty()) {
                        if (dragLocked || rangeLocked) {
                            event.changes.forEach { it.consume() }
                        }
                        break
                    }

                    if (currentRangeCallback != null && pressed.size >= 2) {
                        if (!rangeLocked) {
                            rangeLocked = true
                            dragLocked = true
                            emitScrub(null)
                        }
                        val i1 = indexForX(pressed[0].position.x)
                        val i2 = indexForX(pressed[1].position.x)
                        emitRange(minOf(i1, i2)..maxOf(i1, i2))
                        event.changes.forEach { it.consume() }
                        continue
                    }

                    if (rangeLocked) {
                        event.changes.forEach { it.consume() }
                        continue
                    }

                    val change = event.changes.firstOrNull { it.id == down.id }
                        ?: pressed.firstOrNull()
                        ?: break

                    val delta = change.position - change.previousPosition
                    if (!dragLocked) {
                        totalDx += delta.x
                        totalDy += delta.y
                        val absDx = abs(totalDx)
                        val absDy = abs(totalDy)
                        // Hand off immediately to vertical LazyColumn scroll without triggering scrub recompositions
                        if (absDy > touchSlop && absDy > absDx * 1.15f) {
                            break
                        }
                        if (absDx > touchSlop * 0.5f && absDx >= absDy) {
                            dragLocked = true
                            change.consume()
                            emitScrub(indexForX(change.position.x))
                        }
                    } else {
                        change.consume()
                        emitScrub(indexForX(change.position.x))
                    }
                }
            } finally {
                if (lastScrub != null) {
                    currentScrubCallback(null)
                }
                if (lastRange != null) {
                    currentRangeCallback?.invoke(null)
                }
            }
        }
    }
}

private fun buildSmoothPath(xs: FloatArray, ys: FloatArray, count: Int): Path {
    return Path().apply {
        moveTo(xs[0], ys[0])
        for (i in 1 until count) {
            val prevX = xs[i - 1]
            val prevY = ys[i - 1]
            val currX = xs[i]
            val currY = ys[i]
            val midX = (prevX + currX) * 0.5f
            cubicTo(midX, prevY, midX, currY, currX, currY)
        }
    }
}

fun colorForAssetClass(
    assetClass: AssetClass,
    wealthColors: com.example.ui.theme.WealthExtendedColors
): Color = when (assetClass) {
    AssetClass.STOCK -> wealthColors.stockColor
    AssetClass.ETF -> wealthColors.etfColor
    AssetClass.CASH -> wealthColors.cashColor
    AssetClass.COMMODITY -> wealthColors.commodityColor
}

private val DARK_COMPARISON_PALETTE = listOf(
    Color(0xFF3CCB88), // Luminous Emerald
    Color(0xFFD4B259), // Champagne Gold
    Color(0xFF5FA8D3), // Glacier Blue
    Color(0xFFF08A5D), // Warm Coral
    Color(0xFFC18FD1), // Muted Orchid
    Color(0xFF6DD3CE)  // Seafoam Teal
)

private val LIGHT_COMPARISON_PALETTE = listOf(
    Color(0xFF157A52), // Deep Emerald
    Color(0xFF9A7B2C), // Burnished Gold
    Color(0xFF2E6B8A), // Slate Blue
    Color(0xFFC85A32), // Terracotta
    Color(0xFF7D4E8D), // Editorial Plum
    Color(0xFF1D7874)  // Deep Teal
)

fun comparisonPalette(isDark: Boolean): List<Color> =
    if (isDark) DARK_COMPARISON_PALETTE else LIGHT_COMPARISON_PALETTE
