package io.rhonix.node.api.grpc.methods

import io.grpc.ServerServiceDefinition
import io.rhonix.node.api.grpc.methods.Balances.{balancesMethodDescriptor, balancesServerCallHandler}

object All {

  /// Service (API) description, mapping of methods specification with handlers
  def apply(serviceName: String, getBalance: String => Long): ServerServiceDefinition = ServerServiceDefinition
    .builder(serviceName)
    .addMethod(balancesMethodDescriptor, balancesServerCallHandler(getBalance))
    .build()
}
