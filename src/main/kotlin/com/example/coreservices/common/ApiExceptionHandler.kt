package com.example.coreservices.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun api(error: ApiException) = ResponseEntity.status(error.status).body(mapOf("error" to error.message))

    @ExceptionHandler(MethodArgumentNotValidException::class, HttpMessageNotReadableException::class)
    fun invalid() = ResponseEntity.badRequest().body(mapOf("error" to "Invalid request"))

    @ExceptionHandler(AccessDeniedException::class)
    fun forbidden() = ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "Forbidden"))
}
