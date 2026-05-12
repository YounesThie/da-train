package com.example.traintimes.model

data class TrainSchedule(
    val id: Int,
    val tripId: String,
    val destination: String,
    val departureTime: String,
    val trackNumber: String,
    val status: TrainStatus,
    val transportMode: String = "",
    val lineName: String = ""
)

enum class TrainStatus {
    ON_TIME, DELAYED, CANCELLED
}
