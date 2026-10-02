package com.example.travelrecommender.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TravelPlannerScreen(
    modifier: Modifier = Modifier,
    viewModel: TravelPlannerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loading = state.result is RecommendationState.Loading
    LazyColumn(
        modifier = modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("Travel Planner", style = MaterialTheme.typography.headlineMedium) }
        item {
            OutlinedTextField(
                value = state.travelRequest,
                onValueChange = viewModel::updateTravelRequest,
                label = { Text("What do you feel like doing?") },
                minLines = 3,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Column {
                Text("Available time:")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..4).forEach { hours ->
                        FilterChip(
                            selected = state.availableMinutes == hours * 60,
                            onClick = { viewModel.selectDuration(hours * 60) },
                            label = { Text("${hours}h") },
                            enabled = !loading
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = viewModel::findActivities,
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Find activities") }
        }
        when (val result = state.result) {
            RecommendationState.Idle -> Unit
            RecommendationState.Loading -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Finding activities…")
                }
            }
            is RecommendationState.Error -> item {
                Text(
                    result.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            is RecommendationState.Success -> {
                item {
                    Text(if (result.recommendations.isEmpty()) "No activities found. Try another request."
                    else "Recommended activities", style = MaterialTheme.typography.titleLarge)
                }
                items(result.recommendations) { recommendation ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(recommendation.name, style = MaterialTheme.typography.titleMedium)
                            Text(recommendation.reason)
                            Text("${recommendation.durationMinutes} minutes")
                        }
                    }
                }
            }
        }
    }
}
