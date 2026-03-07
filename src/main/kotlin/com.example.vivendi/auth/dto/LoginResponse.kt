package com.example.vivendi.auth.dto

data class LoginResponse(
    val authToken: String,
    val xsrfToken: String
)