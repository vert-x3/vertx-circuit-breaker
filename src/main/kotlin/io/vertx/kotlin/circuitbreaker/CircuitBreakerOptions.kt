package io.vertx.kotlin.circuitbreaker

import io.vertx.circuitbreaker.CircuitBreakerOptions

fun CircuitBreakerOptions(
    fallbackOnFailure: Boolean? = null,
  maxFailures: Int? = null,
  maxRetries: Int? = null,
  metricsRollingWindow: Long? = null,
  notificationAddress: String? = null,
  notificationPeriod: Long? = null,
  resetTimeout: Long? = null,
  timeout: Long? = null): CircuitBreakerOptions = io.vertx.circuitbreaker.CircuitBreakerOptions().apply {

  if (fallbackOnFailure != null) {
    this.isFallbackOnFailure = fallbackOnFailure
  }

  if (maxFailures != null) {
    this.maxFailures = maxFailures
  }

  if (maxRetries != null) {
    this.maxRetries = maxRetries
  }

  if (metricsRollingWindow != null) {
    this.metricsRollingWindow = metricsRollingWindow
  }

  if (notificationAddress != null) {
    this.notificationAddress = notificationAddress
  }

  if (notificationPeriod != null) {
    this.notificationPeriod = notificationPeriod
  }

  if (resetTimeout != null) {
    this.resetTimeout = resetTimeout
  }

  if (timeout != null) {
    this.timeout = timeout
  }

}

