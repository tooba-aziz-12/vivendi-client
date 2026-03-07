package com.example.vivendi.client

import com.example.vivendi.auth.dto.LoginRequest
import com.example.vivendi.client.dto.RsaKeyResponse
import com.example.vivendi.auth.dto.LoginResponse
import com.example.vivendi.client.dto.LoadFilter
import com.example.vivendi.client.dto.ResidentsGraphQlRequest
import com.example.vivendi.client.dto.ResidentsGraphQlResponse
import com.example.vivendi.client.dto.ResidentsVariables
import com.example.vivendi.residents.dto.*
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

class VivendiClient(
    private val httpClient: HttpClient
) {

    private val baseUrl = "https://vivapp.vivendi.de:4482/api/vivendi/v1"

    suspend fun authenticate(username: String, password: String): LoginResponse {
        val rsaKey = fetchRsaPublicKey()
        val encryptedPassword = encryptVivendiV1Password(password, rsaKey)

        val response: HttpResponse = httpClient.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            header("x-client-product-type", "ng")
            setBody(
                LoginRequest(
                    Username = username,
                    Password = encryptedPassword,
                    PublicKey = rsaKey
                )
            )
        }

        val cookies = response.headers
            .getAll(HttpHeaders.SetCookie)
            .orEmpty()
            .map { parseServerSetCookieHeader(it) }

        val authToken = cookies.firstOrNull { it.name == "Auth-Token" }?.value
            ?: error("Auth-Token cookie missing from login response")

        val xsrfToken = cookies.firstOrNull { it.name == "Xsrf-Token" }?.value
            ?: error("Xsrf-Token cookie missing from login response")

        return LoginResponse(
            authToken = authToken,
            xsrfToken = xsrfToken
        )
    }

    suspend fun getResidents(
        session: LoginResponse,
        fields: List<String>,
        sectionId: Int = 195
    ): List<ResidentResponse> {

        val query = GraphQLQueryBuilder.residents(fields)
        val response: ResidentsGraphQlResponse = httpClient.post("$baseUrl/graphql") {

            contentType(ContentType.Application.Json)

            header("abfrage-hash", "042c8160c5546a719a016009189e8364")
            header("x-client-product-type", "pd")
            header("x-client-version", "26.2.2")
            header("x-section-id", sectionId.toString())
            header("x-xsrf-token", session.xsrfToken)

            cookie("Auth-Token", session.authToken)
            cookie("Xsrf-Token", session.xsrfToken)

            setBody(
                ResidentsGraphQlRequest(
                    operationName = "klientenListe",
                    variables = ResidentsVariables(
                        bereichId = sectionId,
                        nurPdBereiche = true,
                        auchAbwesende = true,
                        mitVerlauf = false,
                        alleVerlaeufe = false,
                        mitPflichtfeldPruefung = false,
                        mitConsilMetaInfos = false,
                        filterTarget = "KLIENTEN_AUSWAHL",
                        withFilter = false,
                        filter = LoadFilter(loadFilter = true)
                    ),
                    query = query
                )
            )
        }.body()

        return response.data.klienten.map {
            ResidentResponse(
                id = it.id,
                firstName = it.vorname,
                lastName = it.name,
                birthDate = it.geburtsdatum
            )
        }
    }

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