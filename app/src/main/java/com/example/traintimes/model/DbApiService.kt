package com.example.traintimes.model

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DbApiService {
    @GET("stops/{id}/departures")
    suspend fun getDepartures(
        @Path("id") stationId: String,
        @Query("results") results: Int = 10,
        @Query("duration") duration: Int = 60
    ): DeparturesResponse

    companion object {
        private const val BASE_URL = "https://v6.db.transport.rest/"

        fun create(): DbApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DbApiService::class.java)
        }
    }
}
