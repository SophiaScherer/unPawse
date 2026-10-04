package com.example.unpawse.ui.stats

import com.example.unpawse.data.apps.DeviceUsageProvider
import com.example.unpawse.data.apps.InstalledApp
import com.example.unpawse.data.apps.InstalledAppsProvider
import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.FakeCaptureDao
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.data.unlocks.FakeUnlockDao
import com.example.unpawse.data.unlocks.UnlockRepository
import com.example.unpawse.data.usage.FakeUsageDao
import com.example.unpawse.data.usage.UsageRepository
import com.example.unpawse.data.usage.UsageScope
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.minutes

/** Which days the Stats screen is observing, and that a new day replaces them. */
@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val zone = ZoneId.of("UTC")

    // A Sunday, so midnight starts a new chart week as well as a new day.
    private val sunday = LocalDate.of(2026, 7, 19)
    private var repoToday = sunday
    private val usage = UsageRepository(FakeUsageDao(), today = { repoToday })
    private val today = MutableStateFlow(sunday)
    private val scope = MutableStateFlow(UsageScope.TRACKED)
    private val device = RecordingDeviceUsage()
    private var nowMillis = 0L

    private class RecordingDeviceUsage : DeviceUsageProvider {
        val requested = mutableListOf<LocalDate>()
        var granted = true
        override suspend fun dailyAverageSeconds(days: Int): Map<String, Long>? = emptyMap()
        override suspend fun dailySecondsByDate(days: Int, endingOn: LocalDate): Map<String, Map<String, Long>>? {
            requested += endingOn
            if (!granted) return null
            return mapOf(endingOn.toString() to mapOf("x" to 120L * 60))
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = StatsViewModel(
        usageRepository = usage,
        captureRepository = CaptureRepository(FakeCaptureDao(), PhotoStorage(tmp.root)),
        unlockRepository = UnlockRepository(FakeUnlockDao(), today = { repoToday }),
        userName = flowOf(""),
        usageScope = scope,
        setUsageScope = { scope.value = it },
        installedAppsProvider = object : InstalledAppsProvider {
            override suspend fun installedApps(): List<InstalledApp> = emptyList()
        },
        deviceUsageProvider = device,
        today = today,
        zone = { zone },
        nowMillis = { nowMillis },
    )

    @Test
    fun `stats moves on to the new day and the new week at midnight`() = runTest {
        usage.setLimit("a", "A", dailyLimitMinutes = 120)
        usage.addUsage("a", 60.minutes)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        assertEquals("1h", vm.uiState.value.dailyTotal)
        assertEquals(6, vm.uiState.value.highlightDayIndex)

        repoToday = sunday.plusDays(1)
        today.value = sunday.plusDays(1)

        val monday = vm.uiState.value
        assertEquals("0m", monday.dailyTotal)
        assertEquals(0, monday.highlightDayIndex)
        // A fresh week: Monday measured, the rest still to come.
        assertEquals(0f, monday.weeklyPoints.first())
        assertNull(monday.weeklyPoints[1])
        assertEquals("100% below yesterday so far", monday.deltaText)
    }

    @Test
    fun `all-apps figures are re-read for the new day`() = runTest {
        scope.value = UsageScope.ALL
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals("2h", vm.uiState.value.dailyTotal)

        today.value = sunday.plusDays(1)

        assertEquals(listOf(sunday, sunday.plusDays(1)), device.requested)
        assertEquals("2h", vm.uiState.value.dailyTotal)
        assertEquals(0, vm.uiState.value.highlightDayIndex)
    }

    @Test
    fun `opening the screen reads the platform once, not once per trigger`() = runTest {
        scope.value = UsageScope.ALL
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        // The Route's resume lands straight after the flow's own start.
        vm.refresh()

        assertEquals(1, device.requested.size)
    }

    @Test
    fun `a resume after the snapshot goes stale reads again`() = runTest {
        scope.value = UsageScope.ALL
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        nowMillis += 60_000L
        vm.refresh()

        assertEquals(2, device.requested.size)
    }

    @Test
    fun `a read without usage access is retried on the next resume`() = runTest {
        // Returning from the grant screen is the read that must never be skipped as fresh.
        scope.value = UsageScope.ALL
        device.granted = false
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(true, vm.uiState.value.scopeUnavailable)

        device.granted = true
        vm.refresh()

        assertEquals(2, device.requested.size)
        assertEquals("2h", vm.uiState.value.dailyTotal)
    }

    @Test
    fun `tracked scope never reads the platform`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        vm.refresh()

        assertEquals(emptyList<LocalDate>(), device.requested)
    }

    @Test
    fun `badges read the live window over the one-shot history`() = runTest {
        // Under Budget for yesterday must judge the final total, not what the history read caught.
        usage.setLimit("a", "A", dailyLimitMinutes = 60)
        usage.addUsage("a", 30.minutes)
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        usage.addUsage("a", 60.minutes)

        repoToday = sunday.plusDays(1)
        today.value = sunday.plusDays(1)

        val underBudget = vm.uiState.value.achievements.single { it.title == "Under Budget" }
        assertEquals(false, underBudget.unlocked)
    }
}
