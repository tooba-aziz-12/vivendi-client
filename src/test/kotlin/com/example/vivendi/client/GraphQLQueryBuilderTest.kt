package com.example.vivendi.client

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GraphQLQueryBuilderTest {

    @Test
    fun `builds query with provided fields`() {
        val query = GraphQLQueryBuilder.residents(
            listOf("id", "name", "vorname")
        )

        assertTrue(query.contains("id"))
        assertTrue(query.contains("name"))
        assertTrue(query.contains("vorname"))
    }

    @Test
    fun `query contains klienten and bereichId`() {
        val query = GraphQLQueryBuilder.residents(
            listOf("id")
        )

        assertTrue(query.contains("klienten"))
        assertTrue(query.contains("bereichId"))
    }
    @Test
    fun `throws when fields empty`() {
        assertFailsWith<IllegalArgumentException> {
            GraphQLQueryBuilder.residents(emptyList())
        }
    }
}