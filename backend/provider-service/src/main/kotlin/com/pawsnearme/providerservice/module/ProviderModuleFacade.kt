package com.pawsnearme.providerservice.module

import com.pawsnearme.common.module.CustomerPetIdentitySnapshot
import com.pawsnearme.common.module.ProviderModuleApi
import com.pawsnearme.common.module.VaccinationReminderSnapshot
import com.pawsnearme.providerservice.repository.PetRepository
import com.pawsnearme.providerservice.repository.ProfileRepository
import com.pawsnearme.providerservice.repository.ProviderRepository
import com.pawsnearme.providerservice.repository.VaccinationReminderRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ProviderModuleFacade(
    private val providerRepository: ProviderRepository,
    private val vaccinationReminderRepository: VaccinationReminderRepository,
    private val profileRepository: ProfileRepository,
    private val petRepository: PetRepository,
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

    override fun customerPetIdentity(customerId: UUID, petId: UUID): CustomerPetIdentitySnapshot? {
        val profile = profileRepository.findById(customerId).orElse(null) ?: return null
        val pet = petRepository.findById(petId).orElse(null) ?: return null
        if (pet.ownerId != customerId) return null
        return CustomerPetIdentitySnapshot(
            customerId = customerId,
            customerName = profile.fullName,
            petId = petId,
            petName = pet.name,
        )
    }
}
