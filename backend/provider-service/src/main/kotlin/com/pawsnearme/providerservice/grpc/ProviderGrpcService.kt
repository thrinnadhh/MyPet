package com.pawsnearme.providerservice.grpc

import com.pawsnearme.providerservice.repository.ProviderRepository
import io.grpc.stub.StreamObserver
import net.devh.boot.grpc.server.service.GrpcService
import java.util.UUID

@GrpcService
class ProviderGrpcService(
    private val providerRepository: ProviderRepository
) : ProviderServiceGrpc.ProviderServiceImplBase() {

    override fun getProviderDetails(
        request: ProviderRequest,
        responseObserver: StreamObserver<ProviderResponse>
    ) {
        try {
            val providerId = UUID.fromString(request.providerId)
            val providerOpt = providerRepository.findById(providerId)

            if (providerOpt.isPresent) {
                val provider = providerOpt.get()
                val response = ProviderResponse.newBuilder()
                    .setProviderId(provider.providerId.toString())
                    .setName(provider.name)
                    .setProviderType(provider.providerType.name)
                    .setFulfillmentType(provider.fulfillmentType.name)
                    .setLatitude(provider.geoLocation.y) // latitude is y coordinate in JTS
                    .setLongitude(provider.geoLocation.x) // longitude is x coordinate in JTS
                    .setStatus(provider.status.name)
                    .build()

                responseObserver.onNext(response)
                responseObserver.onCompleted()
            } else {
                responseObserver.onError(
                    io.grpc.Status.NOT_FOUND
                        .withDescription("Provider with ID $providerId not found")
                        .asRuntimeException()
                )
            }
        } catch (e: Exception) {
            responseObserver.onError(
                io.grpc.Status.INTERNAL
                    .withDescription("Internal error: ${e.message}")
                    .asRuntimeException()
            )
        }
    }
}
