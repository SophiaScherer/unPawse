package com.example.unpawse.ui.home

import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.FakeCaptureDao
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.data.usage.FakeUsageDao
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.service.FocusSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes

/** The wiring [HomeMapperTest] can't reach: which day's rows the screen is actually observing. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val zone = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 7, 16)
    private var repoToday = day
    private val usage = UsageRepository(FakeUsageDao(), today = { repoToday })
    private val ticks = MutableStateFlow(day.atTime(23, 59, 30).atZone(zone))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = HomeViewModel(
        usageRepository = usage,
        captureRepository = CaptureRepository(FakeCaptureDao(), PhotoStorage(tmp.root)),
        userName = flowOf("Sophia"),
        focusSession = FocusSession(),
        usageAccessGranted = { true },
        overlayAccessGranted = { true },
        clockTicks = ticks,
        nowMillis = { 0L },
    )

    private suspend fun seed() {
        usage.setLimit("a", "A", dailyLimitMinutes = 60)
        usage.addUsage("a", 30.minutes)
        repoToday = day.plusDays(1)
        usage.addUsage("a", 5.minutes)
    }

    @Test
    fun `home moves on to the new day's figures at midnight`() = runTest {
        seed()
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals("30m", vm.uiState.value.screenTimeUsedLabel)
        assertEquals("30m", vm.uiState.value.remainingLabel)

        ticks.value = day.plusDays(1).atStartOfDay(zone)

        assertEquals("5m", vm.uiState.value.screenTimeUsedLabel)
        assertEquals("55m", vm.uiState.value.remainingLabel)
    }

    @Test
    fun `the greeting follows the same clock`() = runTest {
        seed()
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals("Good evening,", vm.uiState.value.greeting)

        ticks.value = day.plusDays(1).atTime(7, 0).atZone(zone)

        assertEquals("Good morning,", vm.uiState.value.greeting)
        assertEquals("5m", vm.uiState.value.screenTimeUsedLabel)
    }
}
