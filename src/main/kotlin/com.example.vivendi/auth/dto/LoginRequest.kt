package com.example.vivendi.auth.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val Username: String,
    val Password: String,
    val PublicKey: String
)