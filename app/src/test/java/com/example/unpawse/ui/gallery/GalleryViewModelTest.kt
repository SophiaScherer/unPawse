package com.example.unpawse.ui.gallery

import com.example.unpawse.data.capture.CaptureRepository
import com.example.unpawse.data.capture.CaptureEntity
import com.example.unpawse.data.capture.FakeCaptureDao
import com.example.unpawse.data.capture.PhotoStorage
import com.example.unpawse.data.time.LocalDay
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
import java.time.ZonedDateTime

/** Which day the Gallery files each capture under, as the clock and zone move. */
@OptIn(ExperimentalCoroutinesApi::class)
class GalleryViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val utc = ZoneId.of("UTC")
    private val day = LocalDate.of(2026, 7, 16)
    private val dao = FakeCaptureDao()
    private val today = MutableStateFlow(LocalDay(day, utc))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GalleryViewModel(
        repository = CaptureRepository(dao, PhotoStorage(tmp.root)),
        retentionDays = flowOf(0),
        userName = flowOf(""),
        today = today,
        nowMillis = { 0L },
    )

    @Test
    fun `a zone change on the same date regroups the captures`() = runTest {
        // 02:00 UTC on the 16th is still the evening of the 15th in Los Angeles.
        val at = ZonedDateTime.of(day.atTime(2, 0), utc).toInstant().toEpochMilli()
        dao.insert(CaptureEntity("c", "/x.jpg", at, 0.9f, isBonus = false))
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        assertEquals(listOf("Today"), vm.uiState.value.sections.map { it.title })

        today.value = LocalDay(day, ZoneId.of("America/Los_Angeles"))

        assertEquals(listOf("Yesterday"), vm.uiState.value.sections.map { it.title })
    }

    @Test
    fun `midnight moves today's captures to yesterday`() = runTest {
        val at = ZonedDateTime.of(day.atTime(9, 0), utc).toInstant().toEpochMilli()
        dao.insert(CaptureEntity("c", "/x.jpg", at, 0.9f, isBonus = false))
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        today.value = LocalDay(day.plusDays(1), utc)

        assertEquals(listOf("Yesterday"), vm.uiState.value.sections.map { it.title })
    }
}
