/*
 * Copyright (c) 2011-2016 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.circuitbreaker;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * Circuit breaker configuration options. All time are given in milliseconds.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class CircuitBreakerOptions {

  /**
   * Default timeout in milliseconds.
   */
  public static final long DEFAULT_TIMEOUT = 10000L;

  /**
   * Default number of failures.
   */
  public static final int DEFAULT_MAX_FAILURES = 5;

  /**
   * Default value of the fallback on failure property.
   */
  public static final boolean DEFAULT_FALLBACK_ON_FAILURE = false;

  /**
   * Default time before it attempts to re-close the circuit (half-open state) in milliseconds.
   */
  public static final long DEFAULT_RESET_TIMEOUT = 30000;

  /**
   * Whether circuit breaker state should be delivered only to local consumers by default = {@code true}.
   */
  public static final boolean DEFAULT_NOTIFICATION_LOCAL_ONLY = true;

  /**
   * A default address on which the circuit breakers can send their updates.
   */
  public static final String DEFAULT_NOTIFICATION_ADDRESS = "vertx.circuit-breaker";

  /**
   * Default notification period  in milliseconds.
   */
  public static final long DEFAULT_NOTIFICATION_PERIOD = 2000;

  /**
   * Default rolling window for metrics in milliseconds.
   */
  public static final long DEFAULT_METRICS_ROLLING_WINDOW = 10000;

  /**
   * Default number of buckets used for the rolling window.
   */
  public static final int DEFAULT_METRICS_ROLLING_BUCKETS = 10;

  /**
   * Default number of retries.
   */
  private static final int DEFAULT_MAX_RETRIES = 0;

  /**
   * The default rolling window span in milliseconds.
   */
  private static final int DEFAULT_FAILURES_ROLLING_WINDOW = 10000;

  /**
   * The operation timeout.
   */
  private long timeout = DEFAULT_TIMEOUT;

  /**
   * The max failures.
   */
  private int maxFailures = DEFAULT_MAX_FAILURES;

  /**
   * Whether or not the fallback should be called upon failures.
   */
  private boolean fallbackOnFailure = DEFAULT_FALLBACK_ON_FAILURE;

  /**
   * The reset timeout.
   */
  private long resetTimeout = DEFAULT_RESET_TIMEOUT;

  /**
   * Whether circuit breaker state should be delivered only to local consumers.
   */
  private boolean notificationLocalOnly = DEFAULT_NOTIFICATION_LOCAL_ONLY;

  /**
   * The event bus address on which the circuit breaker state is published.
   */
  private String notificationAddress = null;

  /**
   * The state publication period in ms.
   */
  private long notificationPeriod = DEFAULT_NOTIFICATION_PERIOD;

  /**
   * The number of retries
   */
  private int maxRetries = DEFAULT_MAX_RETRIES;

  /**
   * The metric rolling window in ms.
   */
  private long metricsRollingWindow = DEFAULT_METRICS_ROLLING_WINDOW;

  /**
   * The number of buckets used for the metric rolling window.
   */
  private int metricsRollingBuckets = DEFAULT_METRICS_ROLLING_BUCKETS;

  /**
   * The failure rolling window in ms.
   */
  private long failuresRollingWindow = DEFAULT_FAILURES_ROLLING_WINDOW;

  /**
   * Creates a new instance of {@link CircuitBreakerOptions} using the default values.
   */
  public CircuitBreakerOptions() {
    // Empty constructor
  }

  /**
   * Creates a new instance of {@link CircuitBreakerOptions} by copying the other instance.
   *
   * @param other the instance fo copy
   */
  public CircuitBreakerOptions(CircuitBreakerOptions other) {
    this.timeout = other.timeout;
    this.maxFailures = other.maxFailures;
    this.fallbackOnFailure = other.fallbackOnFailure;
    this.notificationLocalOnly = other.notificationLocalOnly;
    this.notificationAddress = other.notificationAddress;
    this.notificationPeriod = other.notificationPeriod;
    this.resetTimeout = other.resetTimeout;
    this.maxRetries = other.maxRetries;
    this.metricsRollingBuckets = other.metricsRollingBuckets;
    this.metricsRollingWindow = other.metricsRollingWindow;
    this.failuresRollingWindow = other.failuresRollingWindow;
  }

  /**
   * Creates a new instance of {@link CircuitBreakerOptions} from the given json object.
   *
   * @param json the json object
   */
  public CircuitBreakerOptions(JsonObject json) {
    this();
    CircuitBreakerOptionsConverter.fromJson(json, this);
  }

  /**
   * @return a json object representing the current configuration.
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    CircuitBreakerOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * @return the maximum number of failures before opening the circuit.
   */
  public int getMaxFailures() {
    return maxFailures;
  }

  /**
   * Sets the maximum number of failures before opening the circuit.
   *
   * @param maxFailures the number of failures.
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setMaxFailures(int maxFailures) {
    this.maxFailures = maxFailures;
    return this;
  }

  /**
   * @return the configured timeout in milliseconds.
   */
  public long getTimeout() {
    return timeout;
  }

  /**
   * Sets the timeout in milliseconds. If an action is not completed before this timeout, the action is considered as
   * a failure.
   *
   * @param timeoutInMs the timeout, -1 to disable the timeout
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setTimeout(long timeoutInMs) {
    this.timeout = timeoutInMs;
    return this;
  }

  /**
   * @return whether or not the fallback is executed on failures, even when the circuit is closed.
   */
  public boolean isFallbackOnFailure() {
    return fallbackOnFailure;
  }

  /**
   * Sets whether or not the fallback is executed on failure, even when the circuit is closed.
   *
   * @param fallbackOnFailure {@code true} to enable it.
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setFallbackOnFailure(boolean fallbackOnFailure) {
    this.fallbackOnFailure = fallbackOnFailure;
    return this;
  }

  /**
   * @return the time in milliseconds before it attempts to re-close the circuit (by going to the half-open state).
   */
  public long getResetTimeout() {
    return resetTimeout;
  }

  /**
   * Sets the time in ms before it attempts to re-close the circuit (by going to the half-open state). If the circuit
   * is closed when the timeout is reached, nothing happens. {@code -1} disables this feature.
   *
   * @param resetTimeout the time in ms
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setResetTimeout(long resetTimeout) {
    this.resetTimeout = resetTimeout;
    return this;
  }

  /**
   * @return {@code true} if circuit breaker state should be delivered only to local consumers, otherwise {@code false}
   */
  public boolean isNotificationLocalOnly() {
    return notificationLocalOnly;
  }

  /**
   * Whether circuit breaker state should be delivered only to local consumers.
   *
   * @param notificationLocalOnly {@code true} if circuit breaker state should be delivered only to local consumers, otherwise {@code false}
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setNotificationLocalOnly(boolean notificationLocalOnly) {
    this.notificationLocalOnly = notificationLocalOnly;
    return this;
  }

  /**
   * @return the eventbus address on which the circuit breaker events are published. {@code null} if this feature has
   * been disabled.
   */
  public String getNotificationAddress() {
    return notificationAddress;
  }

  /**
   * Sets the event bus address on which the circuit breaker publish its state change.
   *
   * @param notificationAddress the address, {@code null} to disable this feature.
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setNotificationAddress(String notificationAddress) {
    this.notificationAddress = notificationAddress;
    return this;
  }

  /**
   * @return the the period in milliseconds where the circuit breaker send a notification about its state.
   */
  public long getNotificationPeriod() {
    return notificationPeriod;
  }

  /**
   * Configures the period in milliseconds where the circuit breaker send a notification on the event bus with its
   * current state.
   *
   * @param notificationPeriod the period, 0 to disable this feature.
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setNotificationPeriod(long notificationPeriod) {
    this.notificationPeriod = notificationPeriod;
    return this;
  }

  /**
   * @return the configured rolling window for metrics.
   */
  public long getMetricsRollingWindow() {
    return metricsRollingWindow;
  }

  /**
   * Sets the rolling window used for metrics.
   *
   * @param metricsRollingWindow the period in milliseconds.
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setMetricsRollingWindow(long metricsRollingWindow) {
    this.metricsRollingWindow = metricsRollingWindow;
    return this;
  }

  /**
   * @return the configured rolling window for failures.
   */
  public long getFailuresRollingWindow() {
    return failuresRollingWindow;
  }

  /**
   * Sets the rolling window used for metrics.
   *
   * @param metricsRollingWindow the period in milliseconds.
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setFailuresRollingWindow(long failureRollingWindow) {
    this.failuresRollingWindow = failureRollingWindow;
    return this;
  }

  /**
   * @return the configured number of buckets the rolling window is divided into.
   */
  public int getMetricsRollingBuckets() {
    return metricsRollingBuckets;
  }

  /**
   * Sets the configured number of buckets the rolling window is divided into.
   *
   * The following must be true - metrics.rollingStats.timeInMilliseconds % metrics.rollingStats.numBuckets == 0 - otherwise it will throw an exception.
   *
   * In other words, 10000/10 is okay, so is 10000/20 but 10000/7 is not.
   *
   * @param metricsRollingBuckets the number of rolling buckets.
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setMetricsRollingBuckets(int metricsRollingBuckets) {
    this.metricsRollingBuckets = metricsRollingBuckets;
    return this;
  }

  /**
   * @return the number of times the circuit breaker tries to redo the operation before failing
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Configures the number of times the circuit breaker tries to redo the operation before failing.
   *
   * @param maxRetries the number of retries, 0 to disable this feature.
   * @return the current {@link CircuitBreakerOptions} instance
   */
  public CircuitBreakerOptions setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
    return this;
  }
}
