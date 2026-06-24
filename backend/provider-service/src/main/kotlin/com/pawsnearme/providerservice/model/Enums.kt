package com.pawsnearme.providerservice.model

enum class UserRole {
    CUSTOMER, MERCHANT, CAPTAIN, ADMIN
}

enum class ProviderType {
    PET_STORE, VET_HOSPITAL, GROOMING_CENTER
}

enum class FulfillmentType {
    DELIVERY, APPOINTMENT
}

enum class ProviderStatus {
    DRAFT, PENDING_APPROVAL, INFO_REQUESTED, ACTIVE, SUSPENDED, REJECTED
}
