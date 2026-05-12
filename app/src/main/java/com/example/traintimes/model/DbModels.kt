package com.example.traintimes.model

import com.google.gson.annotations.SerializedName

data class Station(
    val type: String,
    val id: String,
    val name: String
)

data class Departure(
    val tripId: String,
    val direction: String?,
    val line: Line,
    val platform: String?,
    val plannedWhen: String,
    @SerializedName("when") val whenTime: String?,
    val delay: Int?,
    val cancelled: Boolean?
)

data class Line(
    val type: String,
    val id: String,
    val name: String,
    val mode: String,
    val product: String
)

data class DeparturesResponse(
    val departures: List<Departure>
)

data class Journey(
    val tripId: String,
    val line: Line,
    val direction: String,
    val stopovers: List<Stopover>
)

data class Stopover(
    val stop: Station,
    val plannedArrival: String?,
    @SerializedName("arrival") val arrivalTime: String?,
    val plannedDeparture: String?,
    @SerializedName("departure") val departureTime: String?,
    val arrivalDelay: Int?,
    val departureDelay: Int?,
    val cancelled: Boolean?
)

data class JourneyResponse(
    val journey: Journey
)
