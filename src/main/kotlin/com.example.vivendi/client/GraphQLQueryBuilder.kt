package com.example.vivendi.client

object GraphQLQueryBuilder {

    fun residents(fields: List<String>): String {
        val fieldBlock = fields.joinToString("\n        ")

        return """
            query klientenListe(${'$'}bereichId: Int) {
              klienten(bereichId: ${'$'}bereichId) {
                $fieldBlock
              }
            }
        """.trimIndent()
    }
}