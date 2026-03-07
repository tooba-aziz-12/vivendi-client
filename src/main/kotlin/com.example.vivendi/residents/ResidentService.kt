package com.example.vivendi.residents

import com.example.vivendi.client.VivendiClient
import com.example.vivendi.auth.dto.LoginResponse
import com.example.vivendi.residents.dto.ResidentResponse

class ResidentService(
    private val client: VivendiClient
) {

    suspend fun getResidents(session: LoginResponse): List<ResidentResponse> =
        client.getResidents(session)
}