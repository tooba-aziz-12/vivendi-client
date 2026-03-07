package com.example.vivendi.auth

import com.example.vivendi.client.VivendiClient
import com.example.vivendi.auth.dto.LoginResponse

class AuthService(
    private val client: VivendiClient
) {

    suspend fun authenticate(username: String, password: String): LoginResponse {
        return client.authenticate(username, password)
    }
}