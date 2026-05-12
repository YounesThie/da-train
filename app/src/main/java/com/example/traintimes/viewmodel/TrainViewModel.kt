package com.example.traintimes.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.traintimes.model.DbApiService
import com.example.traintimes.model.TrainSchedule
import com.example.traintimes.model.TrainStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrainViewModel : ViewModel() {
    private val apiService = DbApiService.create()

    private val _schedules = MutableStateFlow<List<TrainSchedule>>(emptyList())
    val schedules: StateFlow<List<TrainSchedule>> = _schedules.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        // Berlin Hbf (8011160)
        loadSchedules("8011160")
    }

    fun loadSchedules(stationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = apiService.getDepartures(stationId, results = 20)
                val mappedSchedules = response.departures.mapIndexed { index, dep ->
                    val status = if (dep.cancelled == true) {
                        TrainStatus.CANCELLED
                    } else if (dep.delay != null && dep.delay > 0) {
                        TrainStatus.DELAYED
                    } else {
                        TrainStatus.ON_TIME
                    }

                    val timeToParse = dep.whenTime ?: dep.plannedWhen
                    val formattedTime = try {
                        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                        val date = format.parse(timeToParse)
                        if (date != null) {
                            val outputFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                            outputFormat.format(date)
                        } else {
                            "Unknown Time"
                        }
                    } catch (e: Exception) {
                        "Unknown Time"
                    }

                    TrainSchedule(
                        id = index,
                        destination = dep.direction ?: "Unknown",
                        departureTime = formattedTime,
                        trackNumber = "Track ${dep.platform ?: "?"}",
                        status = status
                    )
                }
                _schedules.value = mappedSchedules
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load schedules: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
