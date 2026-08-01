package com.pawsnearme.providerservice.module

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
