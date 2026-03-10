package com.example.vivendi.client

import com.example.vivendi.auth.dto.LoginRequest
import com.example.vivendi.auth.dto.LoginResponse
import com.example.vivendi.client.dto.*
import com.example.vivendi.client.exception.AuthenticationException
import com.example.vivendi.client.exception.VivendiClientException
import com.example.vivendi.residents.dto.*
import com.typesafe.config.Config
import io.ktor.client.HttpClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.typesafe.config.ConfigFactory
import io.ktor.client.plugins.*
import io.ktor.serialization.*
import org.slf4j.LoggerFactory

class VivendiClient(
    private val httpClient: HttpClient,
) {

    private val config: Config = ConfigFactory.load()
    private val baseUrl: String = config.getString("vivendi.baseUrl")

    private val logger = LoggerFactory.getLogger("VivendiClient")
    suspend fun authenticate(username: String, password: String): LoginResponse {
        val rsaKey = fetchRsaPublicKey()
        val encryptedPassword = encryptVivendiV1Password(password, rsaKey)

        val response = executeLogin(username, encryptedPassword, rsaKey)

        if (!response.status.isSuccess()) {
            throw AuthenticationException("Vivendi login failed: ${response.status}")
        }

        val cookies = response.headers
            .getAll(HttpHeaders.SetCookie)
            .orEmpty()
            .map { parseServerSetCookieHeader(it) }
            .associate { it.name to it.value }

        val authToken = cookies["Auth-Token"]
            ?: throw AuthenticationException("Auth-Token cookie missing")

        val xsrfToken = cookies["Xsrf-Token"]
            ?: throw AuthenticationException("Xsrf-Token cookie missing")

        return LoginResponse(authToken, xsrfToken)
    }

    suspend fun getResidents(
        session: LoginResponse,
        fields: List<String>,
        sectionId: Int = 195
    ): List<ResidentResponse> {

        val response = executeResidentsRequest(session, fields, sectionId)

        val data = response.data ?: throw VivendiClientException("Vivendi returned no data")

        return data.klienten.map(::toResidentResponse)
    }

    private suspend fun executeResidentsRequest(
        session: LoginResponse,
        fields: List<String>,
        sectionId: Int
    ): ResidentsGraphQlResponse {

        val httpResponse = httpClient.post("$baseUrl/graphql") {
            contentType(ContentType.Application.Json)

            applyHeaders(session, sectionId)
            applyCookies(session)

            setBody(buildResidentsRequest(fields, sectionId))
        }

        if (!httpResponse.status.isSuccess()) {
            logger.error("Vivendi returned HTTP error {}", httpResponse.status)
            throw VivendiClientException("Vivendi HTTP error: ${httpResponse.status}")
        }

        val response = parseResponse(httpResponse)

        if (response.errors != null) {
            logger.error("Vivendi GraphQL errors: {}", response.errors)
            throw VivendiClientException("Vivendi GraphQL returned errors")
        }

        return response
    }

    private suspend fun executeLogin(
        username: String,
        encryptedPassword: String,
        rsaKey: String
    ): HttpResponse {
        val response = httpClient.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            header("x-client-product-type", "ng")

            setBody(
                LoginRequest(
                    username = username,
                    password = encryptedPassword,
                    publicKey = rsaKey
                )
            )
        }
        return response
    }

    private fun HttpRequestBuilder.applyHeaders(session: LoginResponse, sectionId: Int) {
        header("abfrage-hash", "042c8160c5546a719a016009189e8364")
        header("x-client-product-type", "pd")
        header("x-client-version", "26.2.2")
        header("x-section-id", sectionId.toString())
        header("x-xsrf-token", session.xsrfToken)
    }

    private fun HttpRequestBuilder.applyCookies(session: LoginResponse) {
        cookie("Auth-Token", session.authToken)
        cookie("Xsrf-Token", session.xsrfToken)
    }

    private fun buildResidentsRequest(fields: List<String>, sectionId: Int) =
        ResidentsGraphQlRequest(
            operationName = "klientenListe",
            variables = ResidentsVariables(
                bereichId = sectionId, //clients from area 195
                nurPdBereiche = true, //Only PD areas
                auchAbwesende = true, //include absent clients
                mitVerlauf = false, //Do not include care history
                alleVerlaeufe = false, //If history was included, don't return all
                mitPflichtfeldPruefung = false, //with required field check (probably checking whether the client has missing required fields)
                mitConsilMetaInfos = false, //with consultation case information
            ),
            query = GraphQLQueryBuilder.residents(fields)
        )

    private suspend fun parseResponse(httpResponse: HttpResponse): ResidentsGraphQlResponse =
        try {
            httpResponse.body()
        } catch (e: JsonConvertException) {
            logger.error("Failed to parse Vivendi response", e)
            throw VivendiClientException("Invalid Vivendi response format", e)
        }

    private fun toResidentResponse(resident: ClientNode) =
        ResidentResponse(
            id = resident.id,
            firstName = resident.vorname,
            lastName = resident.name,
            birthDate = resident.geburtsdatum
        )

    private suspend fun fetchRsaPublicKey(): String {
        val response: RsaKeyResponse = httpClient.get("$baseUrl/auth/rsa-key") {
            header("x-client-product-type", "ng")
        }.body()


        return response.publicKey
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun encryptVivendiV1Password(password: String, rsaKey: String): String {
        val keyBytes = Base64.decode(rsaKey)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec) as RSAPublicKey

        val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        rsa.init(
            Cipher.ENCRYPT_MODE,
            publicKey,
            OAEPParameterSpec(
                "SHA-256",
                "MGF1",
                MGF1ParameterSpec("SHA-256"),
                PSource.PSpecified.DEFAULT,
            ),
        )

        return Base64.encode(rsa.doFinal(password.toByteArray()))
    }
}