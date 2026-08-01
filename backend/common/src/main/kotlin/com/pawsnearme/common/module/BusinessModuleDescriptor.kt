package com.pawsnearme.common.module

/**
 * Stable identity exposed by each business module to the consolidated runtime.
 *
 * M2 links modules as dormant libraries. The descriptor is intentionally free
 * of Spring, persistence, messaging and infrastructure types so it can be read
 * without activating the module's legacy service runtime.
 */
interface BusinessModuleDescriptor {
    val id: String
    val displayName: String
    val basePackage: String
    val legacyApplicationClassName: String

    val legacyApplicationClassResource: String
        get() = legacyApplicationClassName.replace('.', '/') + ".class"
}
