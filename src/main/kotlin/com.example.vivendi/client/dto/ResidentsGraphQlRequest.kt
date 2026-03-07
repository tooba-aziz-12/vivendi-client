package com.example.vivendi.client.dto

import kotlinx.serialization.Serializable

@Serializable
data class ResidentsGraphQlRequest(
    val operationName: String,
    val variables: ResidentsVariables,
    val query: String
)

@Serializable
data class ResidentsVariables(
    val bereichId: Int,
    val nurPdBereiche: Boolean,
    val auchAbwesende: Boolean,
    val mitVerlauf: Boolean,
    val alleVerlaeufe: Boolean,
    val mitPflichtfeldPruefung: Boolean,
    val mitConsilMetaInfos: Boolean,
    val filterTarget: String,
    val withFilter: Boolean,
    val filter: LoadFilter
)
@Serializable
data class LoadFilter(
    val loadFilter: Boolean
)