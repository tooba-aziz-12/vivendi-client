package com.example.vivendi.client

import com.example.vivendi.auth.AuthService
import com.example.vivendi.client.http.HttpClientFactory
import com.example.vivendi.residents.ResidentService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class VivendiFlowIntegrationTest {

    @Test
    fun `authenticate and fetch residents via services`() = runTest {

        val httpClient = HttpClientFactory.create()

        val client = VivendiClient(httpClient)
        val authService = AuthService(client)
        val residentService = ResidentService(client)

        val session = authService.authenticate(
            username = "demo01",
            password = "demo01"
        )

        val residents = residentService.getResidents(session)

        assertTrue(residents.isNotEmpty())
    }
}