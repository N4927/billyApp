package com.billyapp.shared.core

/**
 * Generic Result type for Domain Operations.
 *
 * ARCHITECTURE NOTE:
 * We use this Monad to enforce explicit error handling.
 * Exceptions are reserved for "Panic" situations (crashes, bugs),
 * while expected errors (Network, Validation) are treated as data.
 */
sealed class Result<out D, out E> {
    data class Success<out D>(val data: D) : Result<D, Nothing>()
    data class Failure<out E>(val error: E) : Result<Nothing, E>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): D? = if (this is Success) data else null
    fun errorOrNull(): E? = if (this is Failure) error else null
    
    inline fun onSuccess(action: (D) -> Unit): Result<D, E> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (E) -> Unit): Result<D, E> {
        if (this is Failure) action(error)
        return this
    }
}
