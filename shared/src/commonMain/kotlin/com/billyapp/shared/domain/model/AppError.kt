package com.billyapp.shared.domain.model

/**
 * Domain Error Hierarchy.
 *
 * ARCHITECTURE NOTE:
 * These errors represent "Expected Failures" in the business logic.
 * They are platform-agnostic and can be mapped to UI states (Alerts, Toasts).
 */
sealed class AppError {
    
    sealed class Network : AppError() {
        data object NoInternet : Network()
        data object Timeout : Network()
        data class ServerError(val code: Int, val message: String?) : Network()
        data class Serialization(val message: String?) : Network()
        data object Unauthorized : Network()
    }

    sealed class Business : AppError() {
        data object InvalidBid : Business()
        data object UserNotFound : Business()
        data class ValidationFailed(val reason: String) : Business()
    }

    data class Unknown(val message: String?) : AppError()
}
