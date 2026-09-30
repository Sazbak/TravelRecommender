package com.example.travelrecommender.ui

import com.example.travelrecommender.network.RecommendationApi
import com.example.travelrecommender.network.RecommendationDto
import com.example.travelrecommender.network.RecommendationRequestDto
import com.example.travelrecommender.network.RecommendationResponseDto
import java.io.IOException
import java.time.OffsetDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TravelPlannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val submitted = mutableListOf<RecommendationRequestDto>()
    private val recommendation = RecommendationDto("Park", "Scenic", 90)
    private var respond: suspend () -> RecommendationResponseDto = {
        RecommendationResponseDto(listOf(recommendation))
    }
    private val api = object : RecommendationApi {
        override suspend fun recommendations(request: RecommendationRequestDto): RecommendationResponseDto {
            submitted += request
            return respond()
        }
    }
    private val time = OffsetDateTime.parse("2026-09-29T13:00:00+02:00")

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun ready(): TravelPlannerViewModel = TravelPlannerViewModel(api) { time }.apply {
        updateRequest("  Scenic outdoors  ")
        selectDuration(180)
    }

    @Test fun `blank text and missing duration do not call backend`() = runTest(dispatcher) {
        val model = TravelPlannerViewModel(api) { time }
        assertEquals(RecommendationState.Idle, model.uiState.value.result)
        model.updateRequest("   ")
        model.selectDuration(60)
        model.findActivities()
        assertTrue(model.uiState.value.result is RecommendationState.Error)
        val missingDuration = TravelPlannerViewModel(api) { time }
        missingDuration.updateRequest("Outdoors")
        missingDuration.findActivities()
        assertTrue(missingDuration.uiState.value.result is RecommendationState.Error)
        advanceUntilIdle()
        assertTrue(submitted.isEmpty())
    }

    @Test fun `submission sends contract and displays response`() = runTest(dispatcher) {
        val model = ready()
        model.findActivities()
        assertEquals(RecommendationState.Loading, model.uiState.value.result)
        advanceUntilIdle()
        assertEquals(RecommendationRequestDto(47.4979, 19.0402,
            "2026-09-29T13:00:00+02:00", 180, "Scenic outdoors"), submitted.single())
        assertEquals(RecommendationState.Success(listOf(recommendation)), model.uiState.value.result)
    }

    @Test fun `rapid submissions only launch one request`() = runTest(dispatcher) {
        val response = CompletableDeferred<RecommendationResponseDto>()
        respond = { response.await() }
        val model = ready()
        repeat(5) { model.findActivities() }
        runCurrent()
        model.findActivities()
        assertEquals(1, submitted.size)
        response.complete(RecommendationResponseDto(emptyList()))
        advanceUntilIdle()
        assertEquals(RecommendationState.Success(emptyList()), model.uiState.value.result)
    }

    @Test fun `network failure allows retry with fresh submission time`() = runTest(dispatcher) {
        var currentTime = time
        val model = TravelPlannerViewModel(api) { currentTime }
        model.updateRequest("Outdoors")
        model.selectDuration(60)
        respond = { throw IOException("offline") }
        model.findActivities()
        advanceUntilIdle()
        assertTrue((model.uiState.value.result as RecommendationState.Error).message.contains("connection"))
        currentTime = time.plusHours(1)
        respond = { RecommendationResponseDto(emptyList()) }
        model.findActivities()
        advanceUntilIdle()
        assertEquals(currentTime, OffsetDateTime.parse(submitted.last().startDateTime))
        assertTrue(model.uiState.value.result is RecommendationState.Success)
    }

    @Test fun `http and unexpected failures become readable errors`() = runTest(dispatcher) {
        val model = ready()
        respond = { throw HttpException(Response.error<Unit>(503, "unavailable".toResponseBody())) }
        model.findActivities()
        advanceUntilIdle()
        assertTrue((model.uiState.value.result as RecommendationState.Error).message.contains("HTTP 503"))
        respond = { throw IllegalStateException("unexpected") }
        model.findActivities()
        advanceUntilIdle()
        assertTrue((model.uiState.value.result as RecommendationState.Error).message.contains("Something went wrong"))
    }

    @Test fun `cancellation is not presented as an error`() = runTest(dispatcher) {
        respond = { throw CancellationException("cancelled") }
        val model = ready()
        model.findActivities()
        advanceUntilIdle()
        assertEquals(RecommendationState.Idle, model.uiState.value.result)
    }
}
