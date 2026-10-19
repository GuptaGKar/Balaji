package com.balaji.provision.data.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {

    @POST("auth/v1/token")
    suspend fun signIn(
        @Header("apikey") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Query("grant_type") grantType: String = "password",
        @Body body: AuthTokenRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/token")
    suspend fun refresh(
        @Header("apikey") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Query("grant_type") grantType: String = "refresh_token",
        @Body body: RefreshTokenRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/signup")
    suspend fun signUp(
        @Header("apikey") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: AuthTokenRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/logout")
    suspend fun logout(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): Response<Unit>
}

@Serializable
data class AuthTokenRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refresh_token: String
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null
)

@Serializable
data class AuthTokenResponse(
    val access_token: String? = null,
    val refresh_token: String? = null,
    val expires_in: Long? = null,
    val user: AuthUser? = null
)

@Serializable
data class AuthErrorResponse(
    val error: String? = null,
    val error_description: String? = null,
    val msg: String? = null,
    val message: String? = null
)
