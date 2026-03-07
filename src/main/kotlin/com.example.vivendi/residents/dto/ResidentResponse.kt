package com.example.vivendi.residents.dto

data class ResidentResponse(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val birthDate: String?
)