package com.example.vivendi.client

import com.example.vivendi.client.exception.AuthenticationException
import com.example.vivendi.client.http.HttpClientFactory
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*
import java.security.KeyPairGenerator

class VivendiClientAuthTest {

    @Test
    fun `authenticate returns tokens`() = runTest {

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {

                "/api/vivendi/v1/auth/rsa-key" -> {
                    respond(
                        """{"Key":"${generateTestPublicKey()}"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                "/api/vivendi/v1/auth/login" -> {
                    respond(
                        "",
                        headers = headersOf(
                            HttpHeaders.SetCookie, listOf(
                                "Auth-Token=token123;",
                                "Xsrf-Token=xsrf123;"
                            )
                        )
                    )
                }

                else -> error("Unhandled ${request.url}")
            }
        }

        val httpClient = HttpClientFactory.create(engine)
        val client = VivendiClient(httpClient)

        val response = client.authenticate("demo01", "demo01")

        assertEquals("token123", response.authToken)
        assertEquals("xsrf123", response.xsrfToken)
    }

    @Test
    fun `authenticate throws when cookies missing`() = runTest {

        val engine = MockEngine { request ->
            when (request.url.encodedPath) {

                "/api/vivendi/v1/auth/rsa-key" -> {
                    respond(
                        """{"Key":"${generateTestPublicKey()}"}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                "/api/vivendi/v1/auth/login" -> {
                    respond("", HttpStatusCode.OK)
                }

                else -> error("Unhandled ${request.url}")
            }
        }

        val httpClient = HttpClientFactory.create(engine)
        val client = VivendiClient(httpClient)

        assertFailsWith<AuthenticationException> {
            client.authenticate("demo01", "demo01")
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun generateTestPublicKey(): String {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        return Base64.encode(generator.generateKeyPair().public.encoded)
    }
}