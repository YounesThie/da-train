package com.example.traintimes.viewmodel

import androidx.lifecycle.ViewModel
import com.example.traintimes.model.TrainSchedule
import com.example.traintimes.model.TrainStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrainViewModel : ViewModel() {
    private val _schedules = MutableStateFlow<List<TrainSchedule>>(emptyList())
    val schedules: StateFlow<List<TrainSchedule>> = _schedules.asStateFlow()

    init {
        loadSchedules()
    }

    private fun loadSchedules() {
        // Mock data
        _schedules.value = listOf(
            TrainSchedule(1, "New York", "08:00 AM", "Track 1", TrainStatus.ON_TIME),
            TrainSchedule(2, "Chicago", "09:15 AM", "Track 4", TrainStatus.DELAYED),
            TrainSchedule(3, "San Francisco", "10:30 AM", "Track 2", TrainStatus.ON_TIME),
            TrainSchedule(4, "Boston", "11:00 AM", "Track 5", TrainStatus.CANCELLED),
            TrainSchedule(5, "Washington D.C.", "12:45 PM", "Track 3", TrainStatus.ON_TIME)
        )
    }
}
