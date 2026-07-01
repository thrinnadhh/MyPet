package com.pawsnearme.reviewservice.config

import org.hibernate.boot.model.TypeContributions
import org.hibernate.dialect.PostgreSQLDialect
import org.hibernate.service.ServiceRegistry

/**
 * Custom Postgres dialect that treats review_target_type as a plain varchar
 * so Hibernate doesn't try to register it as a named Postgres type.
 */
class CustomPostgresDialect : PostgreSQLDialect() {
    override fun contributeTypes(typeContributions: TypeContributions, serviceRegistry: ServiceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry)
    }
}
