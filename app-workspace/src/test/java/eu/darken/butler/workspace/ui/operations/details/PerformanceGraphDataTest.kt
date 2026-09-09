package eu.darken.butler.workspace.ui.operations.details

import eu.darken.butler.common.files.local.operations.core.PerformanceHistory
import eu.darken.butler.common.files.local.operations.core.PerformanceSample
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * Tests for [PerformanceGraphData] - turning a [PerformanceHistory] into plottable series.
 */
class PerformanceGraphDataTest : BaseTest() {

    private val startTime = Instant.fromEpochMilliseconds(1000)

    private fun sample(
        index: Int,
        bytesPerSecond: Long = 0L,
        itemsPerSecond: Float = 0f,
        totalBytesProcessed: Long = 0L,
        totalItemsProcessed: Int = 0,
    ) = PerformanceSample(
        timestamp = startTime + (index * 100).milliseconds,
        bytesPerSecond = bytesPerSecond,
        itemsPerSecond = itemsPerSecond,
        totalBytesProcessed = totalBytesProcessed,
        totalItemsProcessed = totalItemsProcessed,
    )

    private fun history(
        samples: List<PerformanceSample>,
        totalBytes: Long = 0L,
        totalItems: Int = 0,
    ) = PerformanceHistory(
        samples = samples,
        startTime = startTime,
        totalBytes = totalBytes,
        totalItems = totalItems,
    )

    // ============ BYTE UNIT SELECTION ============

    @Test
    fun `slow transfer is scaled into kilobytes per second`() {
        val history = history(
            samples = (0 until 20).map { i ->
                sample(
                    index = i,
                    bytesPerSecond = 500_000L,
                    itemsPerSecond = 4f,
                    totalBytesProcessed = i * 500_000L,
                    totalItemsProcessed = i,
                )
            },
            totalBytes = 10_000_000L,
            totalItems = 20,
        )

        val data = PerformanceGraphData.from(history).shouldNotBeNull()

        data.byteUnit shouldBe ByteSpeedUnit.KB_S
        data.byteSpeeds.shouldNotBeNull().forEach { it shouldBe 500f }
        data.maxByteSpeed shouldBe 500.0
    }

    @Test
    fun `byte unit is selected from the fastest sample`() {
        fun unitFor(bytesPerSecond: Long): ByteSpeedUnit? {
            val history = history(
                samples = (0 until 20).map { i ->
                    sample(
                        index = i,
                        bytesPerSecond = bytesPerSecond,
                        itemsPerSecond = 1f,
                        totalBytesProcessed = i * 10L,
                        totalItemsProcessed = i,
                    )
                },
                totalItems = 20,
            )
            return PerformanceGraphData.from(history).shouldNotBeNull().byteUnit
        }

        unitFor(999L) shouldBe ByteSpeedUnit.B_S
        unitFor(1_000L) shouldBe ByteSpeedUnit.KB_S
        unitFor(999_999L) shouldBe ByteSpeedUnit.KB_S
        unitFor(1_000_000L) shouldBe ByteSpeedUnit.MB_S
        unitFor(999_999_999L) shouldBe ByteSpeedUnit.MB_S
        unitFor(1_000_000_000L) shouldBe ByteSpeedUnit.GB_S
    }

    @Test
    fun `bytes moved without a measured speed still get a byte series`() {
        val history = history(
            samples = (0 until 20).map { i ->
                sample(
                    index = i,
                    bytesPerSecond = 0L,
                    itemsPerSecond = 2f,
                    totalBytesProcessed = i * 100_000L,
                    totalItemsProcessed = i,
                )
            },
            totalBytes = 2_000_000L,
            totalItems = 20,
        )

        val data = PerformanceGraphData.from(history).shouldNotBeNull()

        data.byteUnit shouldBe ByteSpeedUnit.B_S
        data.byteSpeeds.shouldNotBeNull().forEach { it shouldBe 0f }
        data.maxByteSpeed shouldBe 0.0
    }

    // ============ SERIES PRESENCE ============

    @Test
    fun `item only operation has no byte series`() {
        val history = history(
            samples = (0 until 20).map { i ->
                sample(index = i, itemsPerSecond = 5f, totalItemsProcessed = i)
            },
            totalItems = 20,
        )

        val data = PerformanceGraphData.from(history).shouldNotBeNull()

        data.byteSpeeds shouldBe null
        data.byteUnit shouldBe null
        data.maxByteSpeed shouldBe 0.0
        // 20 samples 100ms apart cover 1.9s, so five grid points
        data.elapsedSeconds shouldBe listOf(0f, 0.5f, 1f, 1.5f, 2f)
    }

    @Test
    fun `unknown size transfer keeps its byte series`() {
        val history = history(
            samples = (0 until 20).map { i ->
                sample(
                    index = i,
                    bytesPerSecond = 4_000_000L,
                    itemsPerSecond = 2f,
                    totalBytesProcessed = i * 4_000_000L,
                    totalItemsProcessed = i,
                )
            },
            totalBytes = 0L,  // Size unknown up front, e.g. a stream copy
            totalItems = 20,
        )

        val data = PerformanceGraphData.from(history).shouldNotBeNull()

        data.byteUnit shouldBe ByteSpeedUnit.MB_S
        data.byteSpeeds.shouldNotBeNull() shouldHaveSize 5
        data.elapsedSeconds shouldBe listOf(0f, 0.5f, 1f, 1.5f, 2f)
    }

    // ============ NO GRAPH ============

    @Test
    fun `a history without totals plots against elapsed time`() {
        val history = history(
            samples = (0 until 20).map { i ->
                sample(index = i, bytesPerSecond = 1_000_000L, itemsPerSecond = 5f)
            },
        )

        val data = PerformanceGraphData.from(history).shouldNotBeNull()

        data.elapsedSeconds shouldBe listOf(0f, 0.5f, 1f, 1.5f, 2f)
        data.byteUnit shouldBe ByteSpeedUnit.MB_S
    }

    @Test
    fun `samples that all carry the same instant are not plottable`() {
        val history = history(
            samples = (0 until 20).map { i ->
                sample(index = 0, itemsPerSecond = 5f, totalItemsProcessed = i)
            },
            totalItems = 20,
        )

        PerformanceGraphData.from(history) shouldBe null
    }

    @Test
    fun `too few samples produce no data`() {
        val history = history(
            samples = (0 until 9).map { i ->
                sample(index = i, itemsPerSecond = 5f, totalItemsProcessed = i)
            },
            totalItems = 9,
        )

        PerformanceGraphData.from(history) shouldBe null
    }

    // ============ X DOMAIN ============

    @Test
    fun `a pinned item counter no longer suppresses the chart`() {
        // Copying 8 large files to a slow target: one item done, bytes crawling
        val history = history(
            samples = (0 until 20).map { i ->
                sample(
                    index = i,
                    bytesPerSecond = 2_000_000L,
                    itemsPerSecond = 0.01f,
                    totalBytesProcessed = 1_300_000_000L + i * 200_000L,
                    totalItemsProcessed = 1,
                )
            },
            totalBytes = 16_222_522_748L,
            totalItems = 8,
        )

        val data = PerformanceGraphData.from(history).shouldNotBeNull()

        data.elapsedSeconds.zipWithNext().forEach { (previous, next) -> (next > previous) shouldBe true }
    }

    @Test
    fun `elapsed x spans the operation's duration, not its completion fraction`() {
        // One sample per second, ending at 95% completion
        val history = history(
            samples = (0 until 20).map { i ->
                sample(index = i * 10, itemsPerSecond = 1f, totalItemsProcessed = i)
            },
            totalItems = 20,
        )

        val data = PerformanceGraphData.from(history).shouldNotBeNull()

        data.elapsedSeconds shouldBe (0 until 20).map { it.toFloat() }
    }

    @Test
    fun `x values stay on the plot grid`() {
        // Wall-clock sampling produces 249/251ms deltas, not exact quarter seconds
        var offsetMs = 0
        val samples = (0 until 20).map { i ->
            val sample = PerformanceSample(
                timestamp = startTime + offsetMs.milliseconds,
                bytesPerSecond = 1_000_000L,
                itemsPerSecond = 5f,
                totalBytesProcessed = i * 1_000_000L,
                totalItemsProcessed = i,
            )
            offsetMs += if (i % 2 == 0) 249 else 251
            sample
        }

        val data = PerformanceGraphData.from(history(samples, totalItems = 20)).shouldNotBeNull()

        data.elapsedSeconds.forEach { (it % PLOT_STEP_SECONDS) shouldBe 0f }
        data.elapsedSeconds.zipWithNext().forEach { (previous, next) -> (next > previous) shouldBe true }
    }

    @Test
    fun `a sample stamped before the start time is clamped to zero`() {
        val samples = listOf(sample(index = -5, itemsPerSecond = 5f)) +
            (0 until 19).map { i -> sample(index = i, itemsPerSecond = 5f, totalItemsProcessed = i) }

        val data = PerformanceGraphData.from(history(samples, totalItems = 20)).shouldNotBeNull()

        data.elapsedSeconds.first() shouldBe 0f
        data.elapsedSeconds.forEach { (it >= 0f) shouldBe true }
    }

    @Test
    fun `a single large file still advances along the time axis`() {
        val history = history(
            samples = (0 until 20).map { i ->
                sample(
                    index = i,
                    bytesPerSecond = 50_000_000L,
                    itemsPerSecond = 0.05f,
                    totalBytesProcessed = i * 50_000_000L,
                    totalItemsProcessed = 0,  // The single file is only counted once it is done
                )
            },
            totalBytes = 1_000_000_000L,
            totalItems = 1,
        )

        val data = PerformanceGraphData.from(history).shouldNotBeNull()

        data.elapsedSeconds shouldBe data.elapsedSeconds.distinct()
        data.elapsedSeconds.zipWithNext().forEach { (previous, next) -> (next > previous) shouldBe true }
    }

    // ============ FILTERING ============

    @Test
    fun `samples are kept once they land on the next grid step`() {
        // 100ms apart, so most samples round onto the grid step of their predecessor
        val samples = (0 until 10).map { i ->
            sample(index = i, itemsPerSecond = (i + 1) * 10f, totalItemsProcessed = i + 1)
        }
        val data = PerformanceGraphData.from(history(samples, totalItems = 1000)).shouldNotBeNull()

        data.elapsedSeconds shouldBe listOf(0f, 0.5f, 1f)
        // The final sample replaced the entry that shared its 1.0s step: (10 + 40 + 100) / 3
        data.itemSpeeds.last() shouldBe (50f plusOrMinus 0.01f)
    }

    @Test
    fun `the plotted points only grow as samples arrive`() {
        val samples = (0 until 24).map { i ->
            sample(index = i, itemsPerSecond = 5f, totalItemsProcessed = i)
        }

        val earlier = PerformanceGraphData.from(history(samples.dropLast(1), totalItems = 24)).shouldNotBeNull()
        val later = PerformanceGraphData.from(history(samples, totalItems = 24)).shouldNotBeNull()

        later.elapsedSeconds.take(earlier.elapsedSeconds.size) shouldBe earlier.elapsedSeconds
        later.elapsedSeconds shouldBe listOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f)
    }

    @Test
    fun `a final sample stamped before the last plotted point replaces it`() {
        val samples = (0 until 10).map { i ->
            sample(index = i, itemsPerSecond = 5f, totalItemsProcessed = i + 1)
        } + sample(index = 2, itemsPerSecond = 5f, totalItemsProcessed = 4)  // Timestamped in the past

        val data = PerformanceGraphData.from(history(samples, totalItems = 10)).shouldNotBeNull()

        data.elapsedSeconds shouldBe listOf(0f, 0.5f, 1f)
        data.itemSpeeds shouldHaveSize 3
    }

    @Test
    fun `a final sample above the last kept step is appended`() {
        val samples = (0 until 11).map { i ->
            sample(index = i, itemsPerSecond = 5f, totalItemsProcessed = i + 1)
        } + sample(index = 40, itemsPerSecond = 5f, totalItemsProcessed = 12)  // Sampling stalled for 3s

        val data = PerformanceGraphData.from(history(samples, totalItems = 20)).shouldNotBeNull()

        data.elapsedSeconds shouldBe listOf(0f, 0.5f, 1f, 4f)
        data.elapsedSeconds.zipWithNext().forEach { (previous, next) -> (next > previous) shouldBe true }
    }

    @Test
    fun `a wall-clock rollback on the final sample does not break x ordering`() {
        val samples = (0 until 10).map { i ->
            sample(index = i, itemsPerSecond = 5f, totalItemsProcessed = i + 1)
        } + sample(index = 4, itemsPerSecond = 50f, totalItemsProcessed = 11)  // Clock jumped backwards

        val data = PerformanceGraphData.from(history(samples, totalItems = 20)).shouldNotBeNull()

        data.elapsedSeconds.zipWithNext().forEach { (previous, next) -> (next > previous) shouldBe true }
        // The rolled back sample took over the last plotted point: (5 + 5 + 50) / 3
        data.itemSpeeds.last() shouldBe (20f plusOrMinus 0.01f)
    }

    // ============ SMOOTHING ============

    @Test
    fun `smoothing only averages over preceding samples`() {
        // 500ms apart, so every sample lands on its own grid step
        val samples = (0 until 15).map { i ->
            sample(index = i * 5, itemsPerSecond = (i + 1).toFloat(), totalItemsProcessed = i + 1)
        }

        val data = PerformanceGraphData.from(history(samples, totalItems = 15)).shouldNotBeNull()

        data.itemSpeeds shouldHaveSize 15
        // Partial window at the start: (1 + 2 + 3 + 4) / 4
        data.itemSpeeds[3] shouldBe (2.5f plusOrMinus 0.001f)
        // Full trailing window of 10: (4 + 5 + … + 13) / 10
        data.itemSpeeds[12] shouldBe (8.5f plusOrMinus 0.001f)
        // The last point knows nothing beyond itself: (6 + 7 + … + 15) / 10
        data.itemSpeeds.last() shouldBe (10.5f plusOrMinus 0.001f)
        data.maxItemSpeed shouldBe (10.5 plusOrMinus 0.001)
    }

    // ============ RECENT SPEEDS ============

    @Test
    fun `recent speeds average the raw samples`() {
        val samples = (0 until 20).map { i ->
            sample(
                index = i,
                bytesPerSecond = (i + 1) * 1_000_000L,
                itemsPerSecond = (i + 1).toFloat(),
                totalBytesProcessed = i * 1_000_000L,
                totalItemsProcessed = i,
            )
        }

        val data = PerformanceGraphData
            .from(history(samples, totalBytes = 20_000_000L, totalItems = 20))
            .shouldNotBeNull()

        // (1 + 2 + … + 20) / 20 = 10.5
        data.recentBytesPerSecond shouldBe 10_500_000L
        data.recentItemsPerSecond shouldBe (10.5f plusOrMinus 0.001f)
    }

    @Test
    fun `recent speeds only cover the last 30 samples`() {
        val samples = (0 until 40).map { i ->
            sample(
                index = i,
                bytesPerSecond = if (i < 10) 500_000_000L else 1_000_000L,
                itemsPerSecond = if (i < 10) 100f else 2f,
                totalBytesProcessed = i * 1_000_000L,
                totalItemsProcessed = i,
            )
        }

        val data = PerformanceGraphData
            .from(history(samples, totalBytes = 40_000_000L, totalItems = 40))
            .shouldNotBeNull()

        data.recentBytesPerSecond shouldBe 1_000_000L
        data.recentItemsPerSecond shouldBe (2f plusOrMinus 0.001f)
    }

    @Test
    fun `recent speeds ignore the filtering and smoothing of the series`() {
        // 100ms apart, so most samples round onto the grid step of their predecessor
        val samples = (0 until 10).map { i ->
            sample(index = i, itemsPerSecond = (i + 1) * 10f, totalItemsProcessed = i + 1)
        }

        val data = PerformanceGraphData.from(history(samples, totalItems = 1000)).shouldNotBeNull()

        data.itemSpeeds shouldHaveSize 3
        // (10 + 20 + … + 100) / 10, over every sample rather than the three plotted points
        data.recentItemsPerSecond shouldBe (55f plusOrMinus 0.001f)
    }
}
