package io.vertx.kotlin.circuitbreaker

import io.vertx.circuitbreaker.CircuitBreakerOptions

/**
 * A function providing a DSL for building [io.vertx.circuitbreaker.CircuitBreakerOptions] objects.
 *
 * Circuit breaker configuration options. All time are given in milliseconds.
 *
 * @param fallbackOnFailure  Sets whether or not the fallback is executed on failure, even when the circuit is closed.
 * @param maxFailures  Sets the maximum number of failures before opening the circuit.
 * @param maxRetries  Configures the number of times the circuit breaker tries to redo the operation before failing.
 * @param metricsRollingWindow  Sets the rolling window used for metrics.
 * @param notificationAddress  Sets the event bus address on which the circuit breaker publish its state change.
 * @param notificationPeriod  Configures the period in milliseconds where the circuit breaker send a notification on the event bus with its current state.
 * @param resetTimeout  Sets the time in ms before it attempts to re-close the circuit (by going to the hal-open state). If the cricuit is closed when the timeout is reached, nothing happens. <code>-1</code> disables this feature.
 * @param timeout  Sets the timeout in milliseconds. If an action is not completed before this timeout, the action is considered as a failure.
 *
 * <p/>
 * NOTE: This function has been automatically generated from the [io.vertx.circuitbreaker.CircuitBreakerOptions original] using Vert.x codegen.
 */
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
    this.setFallbackOnFailure(fallbackOnFailure)
  }
  if (maxFailures != null) {
    this.setMaxFailures(maxFailures)
  }
  if (maxRetries != null) {
    this.setMaxRetries(maxRetries)
  }
  if (metricsRollingWindow != null) {
    this.setMetricsRollingWindow(metricsRollingWindow)
  }
  if (notificationAddress != null) {
    this.setNotificationAddress(notificationAddress)
  }
  if (notificationPeriod != null) {
    this.setNotificationPeriod(notificationPeriod)
  }
  if (resetTimeout != null) {
    this.setResetTimeout(resetTimeout)
  }
  if (timeout != null) {
    this.setTimeout(timeout)
  }
}

