package eu.darken.butler.workspace.ui.operations.details

import eu.darken.butler.common.files.local.operations.core.PerformanceHistory
import eu.darken.butler.common.files.local.operations.core.PerformanceSample
import kotlin.math.round
import kotlin.time.Instant

enum class ByteSpeedUnit(val divisor: Double) {
    B_S(1.0),
    KB_S(1_000.0),
    MB_S(1_000_000.0),
    GB_S(1_000_000_000.0),
}

/** Grid the x values are snapped to, in seconds. */
internal const val PLOT_STEP_SECONDS = 0.5f

/**
 * Plot-ready series for [OperationPerformanceGraph].
 *
 * All series share the [elapsedSeconds] x values, seconds since the operation started, snapped to
 * [PLOT_STEP_SECONDS] steps.
 *
 * [recentBytesPerSecond] and [recentItemsPerSecond] are averages over the raw history, not over the
 * decimated and smoothed series, so they stay the speeds the operation actually reported last.
 */
data class PerformanceGraphData(
    val elapsedSeconds: List<Float>,
    val byteSpeeds: List<Float>?,
    val itemSpeeds: List<Float>,
    val byteUnit: ByteSpeedUnit?,
    val maxByteSpeed: Double,
    val maxItemSpeed: Double,
    val recentBytesPerSecond: Long,
    val recentItemsPerSecond: Float,
) {

    companion object {
        private const val SMOOTHING_WINDOW = 10

        fun from(history: PerformanceHistory): PerformanceGraphData? {
            if (!history.canShowGraph) return null

            val origin = history.startTime ?: history.samples.first().timestamp

            val samples = mutableListOf<PerformanceSample>()
            val elapsedSeconds = mutableListOf<Float>()

            history.samples.forEach { sample ->
                val x = elapsedSecondsOf(sample, origin)
                if (elapsedSeconds.isEmpty() || x > elapsedSeconds.last()) {
                    samples.add(sample)
                    elapsedSeconds.add(x)
                }
            }

            // The final state matters even when it didn't reach the next grid step
            val finalSample = history.samples.last()
            if (samples.last() !== finalSample) {
                val finalX = elapsedSecondsOf(finalSample, origin)
                if (finalX > elapsedSeconds.last()) {
                    samples.add(finalSample)
                    elapsedSeconds.add(finalX)
                } else {
                    // Keeping the plotted x also covers a wall-clock jump moving the final sample back
                    samples[samples.lastIndex] = finalSample
                }
            }

            // Distinct by construction, so this only fires when fewer than two samples survived
            if (elapsedSeconds.distinct().size < 2) return null

            val hasByteData = history.samples.any { it.bytesPerSecond > 0L || it.totalBytesProcessed > 0L }
            val byteUnit = if (hasByteData) unitFor(samples.maxOf { it.bytesPerSecond }) else null

            val byteSpeeds = byteUnit?.let { unit ->
                samples.map { it.bytesPerSecond / unit.divisor }.trailingAverage()
            }
            val itemSpeeds = samples.map { it.itemsPerSecond.toDouble() }.trailingAverage()

            return PerformanceGraphData(
                elapsedSeconds = elapsedSeconds,
                byteSpeeds = byteSpeeds,
                itemSpeeds = itemSpeeds,
                byteUnit = byteUnit,
                maxByteSpeed = byteSpeeds?.max()?.toDouble() ?: 0.0,
                maxItemSpeed = itemSpeeds.max().toDouble(),
                recentBytesPerSecond = history.getRecentBytesPerSecond(),
                recentItemsPerSecond = history.getRecentItemsPerSecond(),
            )
        }

        /**
         * Seconds since [origin], snapped to [PLOT_STEP_SECONDS] steps.
         *
         * The fixed grid keeps the chart's x step exact, bounds how many x values the axis
         * enumerates per draw, and makes a sample's inclusion depend only on earlier samples, so
         * points already plotted are never re-placed.
         */
        private fun elapsedSecondsOf(sample: PerformanceSample, origin: Instant): Float {
            val raw = (sample.timestamp - origin).inWholeMilliseconds / 1000f
            return (round(raw / PLOT_STEP_SECONDS) * PLOT_STEP_SECONDS).coerceAtLeast(0f)
        }

        private fun unitFor(maxBytesPerSecond: Long): ByteSpeedUnit = when {
            maxBytesPerSecond < 1_000L -> ByteSpeedUnit.B_S
            maxBytesPerSecond < 1_000_000L -> ByteSpeedUnit.KB_S
            maxBytesPerSecond < 1_000_000_000L -> ByteSpeedUnit.MB_S
            else -> ByteSpeedUnit.GB_S
        }

        /**
         * Moving average over the current and the previous [window] - 1 values.
         *
         * Trailing, not centered: a point may never be smoothed by values that come after it.
         */
        private fun List<Double>.trailingAverage(window: Int = SMOOTHING_WINDOW): List<Float> = indices.map { i ->
            val start = maxOf(0, i - window + 1)
            var sum = 0.0
            for (j in start..i) sum += this[j]
            (sum / (i - start + 1)).toFloat()
        }
    }
}
