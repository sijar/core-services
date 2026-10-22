package com.example.coreservices.common

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

fun Instant.toPostgresTimestamp(): OffsetDateTime = atOffset(ZoneOffset.UTC)
