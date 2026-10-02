package com.example.travelrecommender.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.travelrecommender.network.RecommendationApi
import com.example.travelrecommender.network.RecommendationDto
import com.example.travelrecommender.network.RecommendationNetwork
import com.example.travelrecommender.network.RecommendationRequestDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// Replace this development location when device location is introduced.
internal object DevelopmentLocation {
    const val latitude = 47.4979
    const val longitude = 19.0402
}

sealed interface RecommendationState {
    data object Idle : RecommendationState
    data object Loading : RecommendationState
    data class Success(val recommendations: List<RecommendationDto>) : RecommendationState
    data class Error(val message: String) : RecommendationState
}

data class TravelPlannerUiState(
    val travelRequest: String = "",
    val availableMinutes: Int? = null,
    val result: RecommendationState = RecommendationState.Idle
)

class TravelPlannerViewModel(
    private val api: RecommendationApi = RecommendationNetwork.api,
    private val now: () -> OffsetDateTime = { OffsetDateTime.now() }
) : ViewModel() {
    private val _uiState = MutableStateFlow(TravelPlannerUiState())
    val uiState = _uiState.asStateFlow()

    fun updateTravelRequest(request: String) {
        if (_uiState.value.result is RecommendationState.Loading) return
        _uiState.value =
            _uiState.value.copy(travelRequest = request, result = RecommendationState.Idle)
    }

    fun selectDuration(minutes: Int) {
        if (_uiState.value.result is RecommendationState.Loading) return
        _uiState.value =
            _uiState.value.copy(availableMinutes = minutes, result = RecommendationState.Idle)
    }

    fun findActivities() {
        val state = _uiState.value
        if (state.result is RecommendationState.Loading) return
        val validationError = when {
            state.travelRequest.isBlank() -> "Describe what you feel like doing."
            state.availableMinutes == null -> "Choose how much time you have."
            else -> null
        }
        if (validationError != null) {
            _uiState.value = state.copy(result = RecommendationState.Error(validationError))
            return
        }
        // Set synchronously so rapid taps cannot enqueue a second request.
        _uiState.value = state.copy(result = RecommendationState.Loading)
        viewModelScope.launch {
            try {
                val response = api.recommendations(
                    RecommendationRequestDto(
                        latitude = DevelopmentLocation.latitude,
                        longitude = DevelopmentLocation.longitude,
                        startDateTime = now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        availableMinutes = requireNotNull(state.availableMinutes),
                        request = state.travelRequest.trim()
                    )
                )
                _uiState.value =
                    state.copy(result = RecommendationState.Success(response.recommendations))
            } catch (cancelled: CancellationException) {
                _uiState.value = state.copy(result = RecommendationState.Idle)
                throw cancelled
            } catch (error: HttpException) {
                _uiState.value = state.copy(
                    result = RecommendationState.Error(
                        "The server could not complete the request (HTTP ${error.code()}). Please try again."
                    )
                )
            } catch (error: IOException) {
                _uiState.value = state.copy(
                    result = RecommendationState.Error(
                        "Could not reach the server. Check your connection and that the local backend is running."
                    )
                )
            } catch (error: Exception) {
                _uiState.value = state.copy(
                    result = RecommendationState.Error(
                        "Something went wrong while loading activities. Please try again."
                    )
                )
            }
        }
    }
}
