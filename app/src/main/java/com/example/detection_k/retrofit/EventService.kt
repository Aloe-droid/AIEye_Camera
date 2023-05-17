package com.example.detection_k.retrofit

import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.Call
import retrofit2.http.Body

interface EventService {
    @Headers("Content-Type: application/json")
    @POST("api/Event/Create")
    fun sendEvent(@Body event: Event): Call<String>
}