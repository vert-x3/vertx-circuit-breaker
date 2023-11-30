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
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.json.JsonObject;

/**
 * Circuit breaker configuration options. All time values are in milliseconds.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class CircuitBreakerOptions {

  /**
   * Default timeout in milliseconds.
   */
  public static final long DEFAULT_TIMEOUT = 10_000L;

  /**
   * Default number of failures after which a closed circuit breaker moves to open.
   */
  public static final int DEFAULT_MAX_FAILURES = 5;

  /**
   * Default value of the {@linkplain #isFallbackOnFailure() fallback on failure} property.
   */
  public static final boolean DEFAULT_FALLBACK_ON_FAILURE = false;

  /**
   * Default time after which an open circuit breaker moves to half-open (in an attempt to re-close) in milliseconds.
   */
  public static final long DEFAULT_RESET_TIMEOUT = 30_000;

  /**
   * Default value of whether circuit breaker state events should be delivered only to local consumers.
   */
  public static final boolean DEFAULT_NOTIFICATION_LOCAL_ONLY = true;

  /**
   * A default address on which the circuit breakers can send their updates.
   */
  public static final String DEFAULT_NOTIFICATION_ADDRESS = "vertx.circuit-breaker";

  /**
   * Default notification period  in milliseconds.
   */
  public static final long DEFAULT_NOTIFICATION_PERIOD = 2_000;

  /**
   * Default length of rolling window for metrics in milliseconds.
   */
  public static final long DEFAULT_METRICS_ROLLING_WINDOW = 10_000;

  /**
   * Default number of buckets used for the metrics rolling window.
   */
  public static final int DEFAULT_METRICS_ROLLING_BUCKETS = 10;

  /**
   * Default number of retries.
   */
  private static final int DEFAULT_MAX_RETRIES = 0;

  /**
   * Default length of rolling window for failures in milliseconds.
   */
  private static final int DEFAULT_FAILURES_ROLLING_WINDOW = 10_000;

  /**
   * The operation timeout.
   */
  private long timeout = DEFAULT_TIMEOUT;

  /**
   * The max failures.
   */
  private int maxFailures = DEFAULT_MAX_FAILURES;

  /**
   * Whether the fallback should be called upon failures.
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
   * Creates a new instance of {@link CircuitBreakerOptions} from the given JSON object.
   *
   * @param json the JSON object
   */
  public CircuitBreakerOptions(JsonObject json) {
    this();
    CircuitBreakerOptionsConverter.fromJson(json, this);
  }

  /**
   * @return a JSON object representing this configuration
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    CircuitBreakerOptionsConverter.toJson(this, json);
    return json;
  }

  /**
   * @return the maximum number of failures before opening the circuit breaker
   */
  public int getMaxFailures() {
    return maxFailures;
  }

  /**
   * Sets the maximum number of failures before opening the circuit breaker.
   *
   * @param maxFailures the number of failures.
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setMaxFailures(int maxFailures) {
    this.maxFailures = maxFailures;
    return this;
  }

  /**
   * @return the configured timeout in milliseconds
   */
  public long getTimeout() {
    return timeout;
  }

  /**
   * Sets the timeout in milliseconds. If an action does not complete before this timeout, the action is considered as
   * a failure.
   *
   * @param timeoutInMs the timeout, -1 to disable the timeout
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setTimeout(long timeoutInMs) {
    this.timeout = timeoutInMs;
    return this;
  }

  /**
   * @return whether the fallback is executed on failures, even when the circuit breaker is closed
   */
  public boolean isFallbackOnFailure() {
    return fallbackOnFailure;
  }

  /**
   * Sets whether the fallback is executed on failure, even when the circuit breaker is closed.
   *
   * @param fallbackOnFailure {@code true} to enable it.
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setFallbackOnFailure(boolean fallbackOnFailure) {
    this.fallbackOnFailure = fallbackOnFailure;
    return this;
  }

  /**
   * @return the time in milliseconds before an open circuit breaker moves to half-open (in an attempt to re-close)
   */
  public long getResetTimeout() {
    return resetTimeout;
  }

  /**
   * Sets the time in milliseconds before an open circuit breaker moves to half-open (in an attempt to re-close).
   * If the circuit breaker is closed when the timeout is reached, nothing happens. {@code -1} disables this feature.
   *
   * @param resetTimeout the time in ms
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setResetTimeout(long resetTimeout) {
    this.resetTimeout = resetTimeout;
    return this;
  }

  /**
   * @return {@code true} if circuit breaker state events should be delivered only to local consumers,
   * {@code false} otherwise
   */
  public boolean isNotificationLocalOnly() {
    return notificationLocalOnly;
  }

  /**
   * Sets whether circuit breaker state events should be delivered only to local consumers.
   *
   * @param notificationLocalOnly {@code true} if circuit breaker state events should be delivered only to local consumers, {@code false} otherwise
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setNotificationLocalOnly(boolean notificationLocalOnly) {
    this.notificationLocalOnly = notificationLocalOnly;
    return this;
  }

  /**
   * @return the eventbus address on which the circuit breaker events are published, or {@code null} if this feature has
   * been disabled
   */
  public String getNotificationAddress() {
    return notificationAddress;
  }

  /**
   * Sets the event bus address on which the circuit breaker publishes its state changes.
   *
   * @param notificationAddress the address, {@code null} to disable this feature
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setNotificationAddress(String notificationAddress) {
    this.notificationAddress = notificationAddress;
    return this;
  }

  /**
   * @return the period in milliseconds in which the circuit breaker sends notifications about its state
   */
  public long getNotificationPeriod() {
    return notificationPeriod;
  }

  /**
   * Sets the period in milliseconds in which the circuit breaker sends notifications on the event bus with its
   * current state.
   *
   * @param notificationPeriod the period, 0 to disable this feature.
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setNotificationPeriod(long notificationPeriod) {
    this.notificationPeriod = notificationPeriod;
    return this;
  }

  /**
   * @return the configured length of rolling window for metrics
   */
  public long getMetricsRollingWindow() {
    return metricsRollingWindow;
  }

  /**
   * Sets the rolling window length used for metrics.
   *
   * @param metricsRollingWindow the period in milliseconds
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setMetricsRollingWindow(long metricsRollingWindow) {
    this.metricsRollingWindow = metricsRollingWindow;
    return this;
  }

  /**
   * @return the configured length of rolling window for failures
   */
  public long getFailuresRollingWindow() {
    return failuresRollingWindow;
  }

  /**
   * Sets the rolling window length used for failures.
   *
   * @param failureRollingWindow the period in milliseconds
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setFailuresRollingWindow(long failureRollingWindow) {
    this.failuresRollingWindow = failureRollingWindow;
    return this;
  }

  /**
   * @return the configured number of buckets the metrics rolling window is divided into
   */
  public int getMetricsRollingBuckets() {
    return metricsRollingBuckets;
  }

  /**
   * Sets the number of buckets the metrics rolling window is divided into.
   * <p>
   * The following must be true: {@code metricsRollingWindow % metricsRollingBuckets == 0},
   * otherwise an exception will be thrown.
   * For example, 10000/10 is okay, so is 10000/20, but 10000/7 is not.
   *
   * @param metricsRollingBuckets the number of buckets
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setMetricsRollingBuckets(int metricsRollingBuckets) {
    this.metricsRollingBuckets = metricsRollingBuckets;
    return this;
  }

  /**
   * @return the number of times the circuit breaker retries an operation before failing
   */
  public int getMaxRetries() {
    return maxRetries;
  }

  /**
   * Sets the number of times the circuit breaker retries an operation before failing.
   *
   * @param maxRetries the number of retries, 0 to disable retrying
   * @return this {@link CircuitBreakerOptions}
   */
  public CircuitBreakerOptions setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
    return this;
  }
}
