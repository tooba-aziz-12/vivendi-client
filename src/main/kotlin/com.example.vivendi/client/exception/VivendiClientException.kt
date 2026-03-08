package com.example.vivendi.client.exception

class VivendiClientException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)