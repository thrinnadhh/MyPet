package com.pawsnearme.providerservice.module

import com.pawsnearme.common.module.ProviderLocationSnapshot
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.VaccinationReminderSnapshot
import com.pawsnearme.providerservice.repository.ProviderRepository
import com.pawsnearme.providerservice.repository.VaccinationReminderRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProviderModuleFacade(
    private val providerRepository: ProviderRepository,
    private val vaccinationReminderRepository: VaccinationReminderRepository
) : ProviderModuleApi {

    override fun ownerUserId(providerId: UUID): UUID? =
        providerRepository.findById(providerId).orElse(null)?.ownerUserId

    override fun location(providerId: UUID): ProviderLocationSnapshot {
        val provider = providerRepository.findById(providerId)
            .orElseThrow { NoSuchElementException("Provider with ID $providerId not found") }
        return ProviderLocationSnapshot(
            providerId = requireNotNull(provider.providerId),
            city = provider.city,
            pincode = provider.pincode,
            latitude = provider.geoLocation.y,
            longitude = provider.geoLocation.x
        )
    }

    override fun enabledVaccinationReminders(): List<VaccinationReminderSnapshot> =
        vaccinationReminderRepository.findByEnabledTrue().map { reminder ->
            VaccinationReminderSnapshot(
                reminderId = requireNotNull(reminder.reminderId) {
                    "Vaccination reminder is missing its identifier"
                },
                ownerId = reminder.ownerId,
                petId = reminder.petId,
                vaccineName = reminder.vaccineName,
                dueDate = reminder.dueDate,
                enabled = reminder.enabled
            )
        }
}