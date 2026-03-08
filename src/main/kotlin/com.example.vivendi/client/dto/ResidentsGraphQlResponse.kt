package com.example.vivendi.client.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ResidentsGraphQlResponse(
    val data: ResidentsData?,
    val errors: JsonElement? = null
)

@Serializable
data class ResidentsData(
    val klienten: List<ClientNode>
)

@Serializable
data class ClientNode(
    val id: Int,
    val name: String,
    val vorname: String,
    val geburtsdatum: String? = null
)