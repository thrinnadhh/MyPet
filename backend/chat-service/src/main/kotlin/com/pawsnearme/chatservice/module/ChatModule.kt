package com.pawsnearme.chatservice.module

import com.pawsnearme.common.module.BusinessModuleDescriptor

object ChatModule : BusinessModuleDescriptor {
    override val id = "chat"
    override val displayName = "Chat"
    override val basePackage = "com.pawsnearme.chatservice"
    override val legacyApplicationClassName = "com.pawsnearme.chatservice.ChatServiceApplication"
}
