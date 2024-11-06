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

import io.vertx.circuitbreaker.impl.CircuitBreakerImpl;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An implementation of the circuit breaker pattern for Vert.x
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@VertxGen
public interface CircuitBreaker {

  /**
   * Creates a new instance of {@link CircuitBreaker}.
   *
   * @param name    the name
   * @param vertx   the Vert.x instance
   * @param options the configuration options
   * @return the created instance
   */
  static CircuitBreaker create(String name, Vertx vertx, CircuitBreakerOptions options) {
    return new CircuitBreakerImpl(name, vertx, options == null ? new CircuitBreakerOptions() : options);
  }

  /**
   * Creates a new instance of {@link CircuitBreaker}, with default options.
   *
   * @param name  the name
   * @param vertx the Vert.x instance
   * @return the created instance
   */
  static CircuitBreaker create(String name, Vertx vertx) {
    return new CircuitBreakerImpl(name, vertx, new CircuitBreakerOptions());
  }

  /**
   * Closes the circuit breaker. It stops sending events on its state on the event bus.
   * <p>
   * This method is not related to the {@code closed} state of the circuit breaker. To move the circuit breaker to the
   * {@code closed} state, use {@link #reset()}.
   */
  @Fluent
  CircuitBreaker close();

  /**
   * Sets a {@link Handler} to be invoked when the circuit breaker state switches to open.
   *
   * @param handler the handler, must not be {@code null}
   * @return this {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker openHandler(Handler<Void> handler);

  /**
   * Sets a {@link Handler} to be invoked when the circuit breaker state switches to half-open.
   *
   * @param handler the handler, must not be {@code null}
   * @return this {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker halfOpenHandler(Handler<Void> handler);

  /**
   * Sets a {@link Handler} to be invoked when the circuit breaker state switches to closed.
   *
   * @param handler the handler, must not be {@code null}
   * @return this {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker closeHandler(Handler<Void> handler);

  /**
   * Executes the given operation with the circuit breaker control. The operation is generally calling an
   * <em>external</em> system. The operation receives a {@link Promise} object as parameter and <strong>must</strong>
   * call {@link Promise#complete(Object)} when the operation has terminated successfully. The operation must also
   * call {@link Promise#fail(Throwable)} in case of a failure.
   * <p>
   * The operation is not invoked if the circuit breaker is open, and the given fallback is called instead.
   * The circuit breaker also monitors whether the operation completes in time. The operation is considered failed
   * if it does not terminate before the configured timeout.
   * <p>
   * This method returns a {@link Future} object to retrieve the status and result of the operation, with the status
   * being a success or a failure. If the fallback is called, the returned future is successfully completed with the
   * value returned from the fallback. If the fallback throws an exception, the returned future is marked as failed.
   *
   * @param command  the operation
   * @param fallback the fallback function; gets an exception as parameter and returns the <em>fallback</em> result
   * @param <T>      the type of result
   * @return a future object completed when the operation or the fallback completes
   */
  <T> Future<T> executeWithFallback(Handler<Promise<T>> command, Function<Throwable, T> fallback);

  /**
   * Executes the given operation with the circuit breaker control. The operation is generally calling an
   * <em>external</em> system. The operation receives a {@link Promise} object as parameter and <strong>must</strong>
   * call {@link Promise#complete(Object)} when the operation has terminated successfully. The operation must also
   * call {@link Promise#fail(Throwable)} in case of a failure.
   * <p>
   * The operation is not invoked if the circuit breaker is open, and the given fallback is called instead.
   * The circuit breaker also monitors whether the operation completes in time. The operation is considered failed
   * if it does not terminate before the configured timeout.
   * <p>
   * This method returns a {@link Future} object to retrieve the status and result of the operation, with the status
   * being a success or a failure. If the fallback is called, the returned future is successfully completed with the
   * value returned from the fallback. If the fallback throws an exception, the returned future is marked as failed.
   *
   * @param command  the operation
   * @param fallback the fallback function; gets an exception as parameter and returns the <em>fallback</em> result
   * @param <T>      the type of result
   * @return a future object completed when the operation or the fallback completes
   */
  <T> Future<T> executeWithFallback(Supplier<Future<T>> command, Function<Throwable, T> fallback);

  /**
   * Same as {@link #executeWithFallback(Handler, Function)} but using the circuit breaker
   * {@linkplain #fallback(Function) default fallback}.
   *
   * @param command the operation
   * @param <T>     the type of result
   * @return a future object completed when the operation or its fallback completes
   */
  <T> Future<T> execute(Handler<Promise<T>> command);

  /**
   * Same as {@link #executeWithFallback(Supplier, Function)} but using the circuit breaker
   * {@linkplain #fallback(Function) default fallback}.
   *
   * @param command the operation
   * @param <T>     the type of result
   * @return a future object completed when the operation or its fallback completes
   */
  <T> Future<T> execute(Supplier<Future<T>> command);

  /**
   * Same as {@link #executeAndReportWithFallback(Promise, Handler, Function)} but using the circuit breaker
   * {@linkplain #fallback(Function) default fallback}.
   *
   * @param resultPromise the promise on which the operation result is reported
   * @param command      the operation
   * @param <T>          the type of result
   * @return this {@link CircuitBreaker}
   */
  @Fluent
  <T> CircuitBreaker executeAndReport(Promise<T> resultPromise, Handler<Promise<T>> command);

  /**
   * Executes the given operation with the circuit breaker control. The operation is generally calling an
   * <em>external</em> system. The operation receives a {@link Promise} object as parameter and <strong>must</strong>
   * call {@link Promise#complete(Object)} when the operation has terminated successfully. The operation must also
   * call {@link Promise#fail(Throwable)} in case of a failure.
   * <p>
   * The operation is not invoked if the circuit breaker is open, and the given fallback is called instead.
   * The circuit breaker also monitors whether the operation completes in time. The operation is considered failed
   * if it does not terminate before the configured timeout.
   * <p>
   * Unlike {@link #executeWithFallback(Handler, Function)}, this method does not return a {@link Future} object, but
   * lets the caller pass a {@link Promise} object on which the result is reported. If the fallback is called, the promise
   * is successfully completed with the value returned by the fallback function. If the fallback throws an exception,
   * the promise is marked as failed.
   *
   * @param resultPromise the promise on which the operation result is reported
   * @param command      the operation
   * @param fallback     the fallback function; gets an exception as parameter and returns the <em>fallback</em> result
   * @param <T>          the type of result
   * @return this {@link CircuitBreaker}
   */
  @Fluent
  <T> CircuitBreaker executeAndReportWithFallback(Promise<T> resultPromise, Handler<Promise<T>> command,
                                                  Function<Throwable, T> fallback);

  /**
   * Sets a <em>default</em> fallback {@link Function} to be invoked when the circuit breaker is open or when failure
   * occurs and {@link CircuitBreakerOptions#isFallbackOnFailure()} is enabled.
   * <p>
   * The function gets the exception as parameter and returns the <em>fallback</em> result.
   *
   * @param handler the fallback handler
   * @return this {@link CircuitBreaker}
   */
  @Fluent
  <T> CircuitBreaker fallback(Function<Throwable, T> handler);

  /**
   * Configures the failure policy for this circuit-breaker.
   *
   * @return the current {@link CircuitBreaker}
   * @see FailurePolicy
   */
  @Fluent
  default <T> CircuitBreaker failurePolicy(FailurePolicy<T> failurePolicy) {
    return this;
  }

  /**
   * Resets the circuit breaker state. The number of recent failures is set to 0 and if the state is half-open,
   * it is set to closed.
   *
   * @return this {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker reset();

  /**
   * Explicitly opens the circuit breaker.
   *
   * @return this {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker open();

  /**
   * @return the current state of this circuit breaker
   */
  CircuitBreakerState state();

  /**
   * @return the current number of recorded failures
   */
  long failureCount();

  /**
   * @return the name of this circuit breaker
   */
  @CacheReturn
  String name();

  /**
   * @deprecated use {@link #retryPolicy(RetryPolicy)} instead
   */
  @Fluent
  @Deprecated
  CircuitBreaker retryPolicy(Function<Integer, Long> retryPolicy);

  /**
   * Set a {@link RetryPolicy} which computes a delay before a retry attempt.
   */
  @Fluent
  CircuitBreaker retryPolicy(RetryPolicy retryPolicy);
}
