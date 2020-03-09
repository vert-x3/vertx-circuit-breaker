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
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

import java.util.function.Function;

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
   * @param options the configuration option
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
   * This method is not related to the {@code close} state of the circuit breaker. To set the circuit breaker in the
   * {@code close} state, use {@link #reset()}.
   */
  @Fluent
  CircuitBreaker close();

  /**
   * Sets a {@link Handler} invoked when the circuit breaker state switches to open.
   *
   * @param handler the handler, must not be {@code null}
   * @return the current {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker openHandler(Handler<Void> handler);

  /**
   * Sets a {@link Handler} invoked when the circuit breaker state switches to half-open.
   *
   * @param handler the handler, must not be {@code null}
   * @return the current {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker halfOpenHandler(Handler<Void> handler);

  /**
   * Sets a {@link Handler} invoked when the circuit breaker state switches to close.
   *
   * @param handler the handler, must not be {@code null}
   * @return the current {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker closeHandler(Handler<Void> handler);

  /**
   * Executes the given operation with the circuit breaker control. The operation is generally calling an
   * <em>external</em> system. The operation receives a {@link Promise} object as parameter and <strong>must</strong>
   * call {@link Promise#complete(Object)} when the operation has terminated successfully. The operation must also
   * call {@link Promise#fail(Throwable)} in case of failure.
   * <p>
   * The operation is not invoked if the circuit breaker is open, and the given fallback is called immediately. The
   * circuit breaker also monitor the completion of the operation before a configure timeout. The operation is
   * considered as failed if it does not terminate in time.
   * <p>
   * This method returns a {@link Future} object to retrieve the status and result of the operation, with the status
   * being a success or a failure. If the fallback is called, the returned future is successfully completed with the
   * value returned from the fallback. If the fallback throws an exception, the returned future is marked as failed.
   *
   * @param command  the operation
   * @param fallback the fallback function. It gets an exception as parameter and returns the <em>fallback</em> result
   * @param <T>      the type of result
   * @return a future object completed when the operation or its fallback completes
   */
  <T> Future<T> executeWithFallback(Handler<Promise<T>> command, Function<Throwable, T> fallback);

  /**
   * Same as {@link #executeWithFallback(Handler, Function)} but using a callback.
   *
   * @param command  the operation
   * @param fallback the fallback
   * @param handler  the completion handler receiving either the operation result or the fallback result. The
   *                 parameter is an {@link AsyncResult} because if the fallback is not called, the error is passed
   *                 to the handler.
   * @param <T>      the type of result
   */
  default <T> void executeWithFallback(Handler<Promise<T>> command, Function<Throwable, T> fallback,
                                              Handler<AsyncResult<T>> handler) {
    Future<T> fut = executeWithFallback(command, fallback);
    fut.onComplete(handler);
  }

  /**
   * Same as {@link #executeWithFallback(Handler, Function)} but using the circuit breaker default fallback.
   *
   * @param command the operation
   * @param <T>     the type of result
   * @return a future object completed when the operation or its fallback completes
   */
  <T> Future<T> execute(Handler<Promise<T>> command);

  /**
   * Same as {@link #executeWithFallback(Handler, Function)} but using the circuit breaker default fallback.
   *
   * @param command the operation
   * @param handler  the completion handler receiving either the operation result or the fallback result. The
   *                 parameter is an {@link AsyncResult} because if the fallback is not called, the error is passed
   *                 to the handler.
   * @param <T>     the type of result
   */
  default <T> void execute(Handler<Promise<T>> command, Handler<AsyncResult<T>> handler) {
    Future<T> fut = execute(command);
    fut.onComplete(handler);
  }

  /**
   * Same as {@link #executeAndReportWithFallback(Promise, Handler, Function)} but using the circuit breaker default
   * fallback.
   *
   * @param resultPromise the promise on which the operation result is reported
   * @param command      the operation
   * @param <T>          the type of result
   * @return the current {@link CircuitBreaker}
   */
  @Fluent
  <T> CircuitBreaker executeAndReport(Promise<T> resultPromise, Handler<Promise<T>> command);

  /**
   * Executes the given operation with the circuit breaker control. The operation is generally calling an
   * <em>external</em> system. The operation receives a {@link Promise} object as parameter and <strong>must</strong>
   * call {@link Promise#complete(Object)} when the operation has terminated successfully. The operation must also
   * call {@link Promise#fail(Throwable)} in case of failure.
   * <p>
   * The operation is not invoked if the circuit breaker is open, and the given fallback is called immediately. The
   * circuit breaker also monitor the completion of the operation before a configure timeout. The operation is
   * considered as failed if it does not terminate in time.
   * <p>
   * Unlike {@link #executeWithFallback(Handler, Function)},  this method does return a {@link Future} object, but
   * let the caller pass a {@link Future} object on which the result is reported. If the fallback is called, the future
   * is successfully completed with the value returned by the fallback function. If the fallback throws an exception,
   * the future is marked as failed.
   *
   * @param resultPromise the promise on which the operation result is reported
   * @param command      the operation
   * @param fallback     the fallback function. It gets an exception as parameter and returns the <em>fallback</em> result
   * @param <T>          the type of result
   * @return the current {@link CircuitBreaker}
   */
  @Fluent
  <T> CircuitBreaker executeAndReportWithFallback(Promise<T> resultPromise, Handler<Promise<T>> command,
                                                  Function<Throwable, T> fallback);

  /**
   * Sets a <em>default</em> {@link Function} invoked when the bridge is open to handle the "request", or on failure
   * if {@link CircuitBreakerOptions#isFallbackOnFailure()} is enabled.
   * <p>
   * The function gets the exception as parameter and returns the <em>fallback</em> result.
   *
   * @param handler the handler
   * @return the current {@link CircuitBreaker}
   */
  @Fluent
  <T> CircuitBreaker fallback(Function<Throwable, T> handler);

  /**
   * Resets the circuit breaker state (number of failure set to 0 and state set to closed).
   *
   * @return the current {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker reset();

  /**
   * Explicitly opens the circuit.
   *
   * @return the current {@link CircuitBreaker}
   */
  @Fluent
  CircuitBreaker open();

  /**
   * @return the current state.
   */
  CircuitBreakerState state();

  /**
   * @return the current number of failures.
   */
  long failureCount();

  /**
   * @return the name of the circuit breaker.
   */
  @CacheReturn
  String name();

  @Fluent
  CircuitBreaker retryPolicy(Function<Integer, Long> retryPolicy);
}
