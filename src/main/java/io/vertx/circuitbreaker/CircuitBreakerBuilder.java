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

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;

import java.util.function.Function;

/**
 * A builder for {@link CircuitBreaker}.
 * <p>
 * Unlike {@link CircuitBreaker#openHandler(Handler)}, {@link CircuitBreaker#halfOpenHandler(Handler)} and
 * {@link CircuitBreaker#closeHandler(Handler)}, the handlers configured through this builder are baked in as
 * immutable when {@link #build()} is called: calling the corresponding mutator method again on the built circuit
 * breaker throws an {@link IllegalStateException}.
 */
@VertxGen
public interface CircuitBreakerBuilder {

  /**
   * Configure the circuit breaker options.
   *
   * @param options the configuration options
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  CircuitBreakerBuilder with(CircuitBreakerOptions options);

  /**
   * Set the handler invoked when the circuit breaker state switches to open.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  CircuitBreakerBuilder openHandler(Handler<Void> handler);

  /**
   * Set the handler invoked when the circuit breaker state switches to half-open.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  CircuitBreakerBuilder halfOpenHandler(Handler<Void> handler);

  /**
   * Set the handler invoked when the circuit breaker state switches to closed.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  CircuitBreakerBuilder closeHandler(Handler<Void> handler);

  /**
   * Set the default fallback function, see {@link CircuitBreaker#fallback(Function)}.
   *
   * @param handler the fallback function
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  <T> CircuitBreakerBuilder fallback(Function<Throwable, T> handler);

  /**
   * Set the failure policy, see {@link CircuitBreaker#failurePolicy(FailurePolicy)}.
   *
   * @param failurePolicy the failure policy
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  <T> CircuitBreakerBuilder failurePolicy(FailurePolicy<T> failurePolicy);

  /**
   * Set the retry policy, see {@link CircuitBreaker#retryPolicy(RetryPolicy)}.
   *
   * @param retryPolicy the retry policy
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  CircuitBreakerBuilder retryPolicy(RetryPolicy retryPolicy);

  /**
   * Build and return the circuit breaker.
   *
   * @return the circuit breaker as configured by this builder
   */
  CircuitBreaker build();
}
