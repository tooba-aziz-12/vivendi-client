package com.example.vivendi.client.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RsaKeyResponse(
    @SerialName("Key")
    val publicKey: String
)