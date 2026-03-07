package com.example.vivendi.client

import com.example.vivendi.auth.dto.LoginResponse
import com.example.vivendi.client.http.HttpClientFactory
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class VivendiClientResidentTest {

    @Test
    fun `getResidents maps response correctly`() = runTest {

        val responseJson = """
        {
          "data": {
            "klienten": [
              {
                "id": 1,
                "name": "Doe",
                "vorname": "John",
                "geburtsdatum": "1990-01-01"
              }
            ]
          }
        }
        """

        val engine = MockEngine {
            respond(
                content = responseJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val httpClient = HttpClientFactory.create(engine)
        val client = VivendiClient(httpClient)

        val residents = client.getResidents(
            LoginResponse("token", "xsrf"),
            listOf("id", "name", "vorname", "geburtsdatum")
        )

        assertEquals(1, residents.size)
        assertEquals("John", residents.first().firstName)
    }
}