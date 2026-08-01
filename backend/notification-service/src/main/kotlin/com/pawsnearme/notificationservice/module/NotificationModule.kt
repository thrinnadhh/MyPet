package com.pawsnearme.notificationservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object NotificationModule : BusinessModuleDescriptor {
    override val id = "notification"
    override val displayName = "Notification"
    override val basePackage = "com.pawsnearme.notificationservice"
    override val legacyApplicationClassName = "com.pawsnearme.notificationservice.NotificationServiceApplication"
}
