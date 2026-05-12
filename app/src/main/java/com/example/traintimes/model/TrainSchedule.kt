package com.example.traintimes.model

data class TrainSchedule(
    val id: Int,
    val destination: String,
    val departureTime: String,
    val trackNumber: String,
    val status: TrainStatus
)

enum class TrainStatus {
    ON_TIME, DELAYED, CANCELLED
}
