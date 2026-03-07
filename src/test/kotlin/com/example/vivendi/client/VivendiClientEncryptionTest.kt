package com.example.vivendi.client

import io.ktor.client.*
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.DefaultAsserter.assertNotEquals
import kotlin.test.assertNotEquals

class VivendiClientEncryptionTest {

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `encrypt password returns base64 string`() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        val rsaKey = Base64.encode(keyPair.public.encoded)

        val client = VivendiClient(HttpClient())
        val encrypted = client.encryptVivendiV1Password("demo01", rsaKey)

        assertTrue(encrypted.isNotBlank())
        assertNotEquals("demo01", encrypted)
    }
}