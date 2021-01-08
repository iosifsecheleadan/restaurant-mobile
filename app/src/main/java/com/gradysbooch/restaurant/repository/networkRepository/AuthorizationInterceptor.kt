package com.gradysbooch.restaurant.repository.networkRepository

import okhttp3.Interceptor
import okhttp3.Response

class AuthorizationInterceptor(var token: String): Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .addHeader("Authorization", token)
            .build()

        return chain.proceed(request)
    }
}