package com.example.traintimes.viewmodel

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.traintimes.model.DbApiService
import com.example.traintimes.model.Journey
import com.example.traintimes.model.Station
import com.example.traintimes.model.TrainSchedule
import com.example.traintimes.model.TrainStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrainViewModel(private val dataStore: DataStore<Preferences>) : ViewModel() {
    private val apiService = DbApiService.create()

    private val _schedules = MutableStateFlow<List<TrainSchedule>>(emptyList())
    val schedules: StateFlow<List<TrainSchedule>> = _schedules.asStateFlow()

    // Original un-filtered schedules to apply filters locally
    private var allDepartures: List<TrainSchedule> = emptyList()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Station Search State
    private val _searchResults = MutableStateFlow<List<Station>>(emptyList())
    val searchResults: StateFlow<List<Station>> = _searchResults.asStateFlow()

    // Defaults to Berlin Hbf, but will be updated from DataStore in init
    private val _currentStation = MutableStateFlow(Station("stop", "8011160", "Berlin Hbf"))
    val currentStation: StateFlow<Station> = _currentStation.asStateFlow()

    // Journey Details
    private val _selectedJourney = MutableStateFlow<Journey?>(null)
    val selectedJourney: StateFlow<Journey?> = _selectedJourney.asStateFlow()

    private val _isJourneyLoading = MutableStateFlow(false)
    val isJourneyLoading: StateFlow<Boolean> = _isJourneyLoading.asStateFlow()

    private var autoRefreshJob: Job? = null
    private var searchJob: Job? = null

    // Selected Filter (null means all)
    private val _selectedFilter = MutableStateFlow<String?>(null)
    val selectedFilter: StateFlow<String?> = _selectedFilter.asStateFlow()

    // DataStore keys
    private val STATION_ID_KEY = stringPreferencesKey("station_id")
    private val STATION_NAME_KEY = stringPreferencesKey("station_name")

    init {
        viewModelScope.launch {
            // Read from DataStore
            val preferences = dataStore.data.first()
            val savedId = preferences[STATION_ID_KEY]
            val savedName = preferences[STATION_NAME_KEY]
            if (savedId != null && savedName != null) {
                _currentStation.value = Station("stop", savedId, savedName)
            }
            loadSchedules(_currentStation.value.id)
            startAutoRefresh()
        }
    }

    fun searchStations(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            // Debounce delay
            delay(500)
            try {
                val results = apiService.searchStations(query)
                // Filter to only stops
                _searchResults.value = results.filter { it.type == "stop" || it.type == "station" }
            } catch (e: Exception) {
                // Ignore search errors
            }
        }
    }

    fun selectStation(station: Station) {
        _currentStation.value = station
        _searchResults.value = emptyList() // Clear search results

        viewModelScope.launch {
            dataStore.edit { preferences ->
                preferences[STATION_ID_KEY] = station.id
                preferences[STATION_NAME_KEY] = station.name
            }
        }

        loadSchedules(station.id)
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            loadSchedulesInternal(_currentStation.value.id)
            _isRefreshing.value = false
        }
    }

    fun loadSchedules(stationId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            loadSchedulesInternal(stationId)
            _isLoading.value = false
        }
    }

    fun loadJourney(tripId: String) {
        viewModelScope.launch {
            _isJourneyLoading.value = true
            try {
                val response = apiService.getJourneyDetails(tripId)
                _selectedJourney.value = response.journey
            } catch (e: Exception) {
                // Ignore for now
                _selectedJourney.value = null
            } finally {
                _isJourneyLoading.value = false
            }
        }
    }

    fun clearJourney() {
        _selectedJourney.value = null
    }

    fun setFilter(filter: String?) {
        _selectedFilter.value = filter
        applyFilter()
    }

    private fun applyFilter() {
        val currentFilter = _selectedFilter.value
        if (currentFilter == null) {
            _schedules.value = allDepartures
        } else {
            _schedules.value = allDepartures.filter { it.transportMode == currentFilter }
        }
    }

    private suspend fun loadSchedulesInternal(stationId: String) {
        try {
            val response = apiService.getDepartures(stationId, results = 40)
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
                        val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        outputFormat.format(date)
                    } else {
                        "Unknown Time"
                    }
                } catch (e: Exception) {
                    "Unknown Time"
                }

                // Classify product type
                val product = dep.line.product
                val transportMode = when (product) {
                    "nationalExpress" -> "ICE/IC"
                    "national" -> "ICE/IC"
                    "regionalExp" -> "Regional"
                    "regional" -> "Regional"
                    "suburban" -> "S-Bahn"
                    "subway" -> "U-Bahn"
                    "tram" -> "Tram"
                    "bus" -> "Bus"
                    else -> "Other"
                }

                TrainSchedule(
                    id = index,
                    tripId = dep.tripId,
                    destination = dep.direction ?: "Unknown",
                    departureTime = formattedTime,
                    trackNumber = "Track ${dep.platform ?: "?"}",
                    status = status,
                    transportMode = transportMode,
                    lineName = dep.line.name
                )
            }
            allDepartures = mappedSchedules
            applyFilter()
        } catch (e: Exception) {
            _errorMessage.value = "Failed to load schedules: ${e.message}"
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000) // 60 seconds
                // Background refresh doesn't show loading indicator
                loadSchedulesInternal(_currentStation.value.id)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        searchJob?.cancel()
    }
}

class TrainViewModelFactory(private val dataStore: DataStore<Preferences>) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TrainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TrainViewModel(dataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
