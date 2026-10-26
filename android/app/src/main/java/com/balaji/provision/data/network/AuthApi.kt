package com.balaji.provision.data.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface AuthApi {

    @POST("auth/v1/otp")
    suspend fun sendOtp(
        @Header("apikey") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: OtpRequest
    ): Response<Unit>

    @POST("auth/v1/verify")
    suspend fun verifyOtp(
        @Header("apikey") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: VerifyOtpRequest
    ): Response<AuthTokenResponse>

    @POST("auth/v1/token")
    suspend fun signInWithPassword(
        @Header("apikey") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Query("grant_type") grantType: String = "password",
        @Body body: PasswordGrantRequest
    ): Response<AuthTokenResponse>

    @PUT("auth/v1/user")
    suspend fun updatePassword(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: UpdatePasswordRequest
    ): Response<AuthUser>

    @POST("rest/v1/rpc/complete_password_change")
    suspend fun completePasswordChange(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body body: Map<String, String> = emptyMap()
    ): Response<CompletePasswordChangeResponse>

    @POST("auth/v1/token")
    suspend fun refresh(
        @Header("apikey") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Query("grant_type") grantType: String = "refresh_token",
        @Body body: RefreshTokenRequest
    ): Response<AuthTokenResponse>

    @GET("auth/v1/user")
    suspend fun getUser(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): Response<AuthUser>

    @POST("auth/v1/logout")
    suspend fun logout(
        @Header("apikey") apiKey: String,
        @Header("Authorization") authorization: String
    ): Response<Unit>
}

@Serializable
data class OtpRequest(
    val phone: String,
    val create_user: Boolean
)

@Serializable
data class VerifyOtpRequest(
    val phone: String,
    val token: String,
    val type: String
)

@Serializable
data class RefreshTokenRequest(
    val refresh_token: String
)

@Serializable
data class PasswordGrantRequest(
    val email: String,
    val password: String
)

@Serializable
data class UpdatePasswordRequest(
    val password: String
)

@Serializable
data class CompletePasswordChangeResponse(
    val success: Boolean? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class AuthUser(
    val id: String,
    val email: String? = null,
    val phone: String? = null
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
    val error_code: String? = null,
    val error_description: String? = null,
    val msg: String? = null,
    val message: String? = null
)
