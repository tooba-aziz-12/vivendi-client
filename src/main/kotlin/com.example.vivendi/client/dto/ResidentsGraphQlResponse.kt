package com.example.vivendi.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResidentsGraphQlResponse(
    val data: ResidentsData
)

@Serializable
data class ResidentsData(
    val klienten: List<KlientNode>
)

@Serializable
data class KlientNode(
    val id: Int,
    val name: String,
    val vorname: String,
    val geburtsdatum: String? = null
)