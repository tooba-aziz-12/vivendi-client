package com.example.vivendi.client

import com.example.vivendi.auth.dto.LoginResponse
import com.example.vivendi.client.exception.VivendiClientException
import com.example.vivendi.client.http.HttpClientFactory
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.*

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

    @Test
    fun `throws exception when graphql errors returned`() = runTest {

        val responseJson = """
        {
          "data": null,
          "errors": [
            {"message": "Something failed"}
          ]
        }
        """

        val engine = MockEngine {
            respond(
                responseJson,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = VivendiClient(HttpClientFactory.create(engine))

        val ex = assertFailsWith<VivendiClientException> {
            client.getResidents(
                LoginResponse("token", "xsrf"),
                listOf("id")
            )
        }
        assertEquals("Vivendi GraphQL returned errors", ex.message)
    }

    @Test
    fun `throws exception when data missing`() = runTest {

        val responseJson = """
        {
          "data": null
        }
        """

        val engine = MockEngine {
            respond(
                responseJson,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = VivendiClient(HttpClientFactory.create(engine))

        val ex = assertFailsWith<VivendiClientException> {
            client.getResidents(
                LoginResponse("token", "xsrf"),
                listOf("id")
            )
        }

        assertEquals("Vivendi returned no data", ex.message)
    }

    @Test
    fun `throws exception on http error`() = runTest {

        val engine = MockEngine {
            respond(
                "Internal error",
                status = HttpStatusCode.InternalServerError
            )
        }

        val client = VivendiClient(HttpClientFactory.create(engine))

        val ex = assertFailsWith<VivendiClientException> {
            client.getResidents(
                LoginResponse("token", "xsrf"),
                listOf("id")
            )
        }
        assertEquals("Vivendi HTTP error: ${HttpStatusCode.InternalServerError}", ex.message)
    }

    @Test
    fun `throws exception when response json invalid`() = runTest {

        val invalidJson = """
            {
              "data": {
                "klienten": "not-a-list"
              }
            }
        """

        val engine = MockEngine {
            respond(
                invalidJson,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = VivendiClient(HttpClientFactory.create(engine))

        val ex = assertFailsWith<VivendiClientException> {
            client.getResidents(
                LoginResponse("token", "xsrf"),
                listOf("id")
            )
        }

        assertEquals("Invalid Vivendi response format", ex.message)
    }
}