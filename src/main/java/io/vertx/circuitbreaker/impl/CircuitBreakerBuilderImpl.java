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

package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerBuilder;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.FailurePolicy;
import io.vertx.circuitbreaker.RetryPolicy;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.function.Function;

public class CircuitBreakerBuilderImpl implements CircuitBreakerBuilder {

  private final String name;
  private final Vertx vertx;
  private CircuitBreakerOptions options;
  private Handler<Void> openHandler;
  private Handler<Void> halfOpenHandler;
  private Handler<Void> closeHandler;
  private Function fallback;
  private FailurePolicy failurePolicy;
  private RetryPolicy retryPolicy;

  public CircuitBreakerBuilderImpl(String name, Vertx vertx) {
    this.name = Objects.requireNonNull(name);
    this.vertx = Objects.requireNonNull(vertx);
  }

  @Override
  public CircuitBreakerBuilder with(CircuitBreakerOptions options) {
    this.options = options;
    return this;
  }

  @Override
  public CircuitBreakerBuilder openHandler(Handler<Void> handler) {
    this.openHandler = Objects.requireNonNull(handler);
    return this;
  }

  @Override
  public CircuitBreakerBuilder halfOpenHandler(Handler<Void> handler) {
    this.halfOpenHandler = Objects.requireNonNull(handler);
    return this;
  }

  @Override
  public CircuitBreakerBuilder closeHandler(Handler<Void> handler) {
    this.closeHandler = Objects.requireNonNull(handler);
    return this;
  }

  @Override
  public <T> CircuitBreakerBuilder fallback(Function<Throwable, T> handler) {
    this.fallback = Objects.requireNonNull(handler);
    return this;
  }

  @Override
  public <T> CircuitBreakerBuilder failurePolicy(FailurePolicy<T> failurePolicy) {
    this.failurePolicy = Objects.requireNonNull(failurePolicy);
    return this;
  }

  @Override
  public CircuitBreakerBuilder retryPolicy(RetryPolicy retryPolicy) {
    this.retryPolicy = Objects.requireNonNull(retryPolicy);
    return this;
  }

  @Override
  @SuppressWarnings("unchecked")
  public CircuitBreaker build() {
    CircuitBreakerImpl impl = new CircuitBreakerImpl(name, vertx, options, openHandler, halfOpenHandler, closeHandler);
    if (fallback != null) {
      impl.fallback(fallback);
    }
    if (failurePolicy != null) {
      impl.failurePolicy(failurePolicy);
    }
    if (retryPolicy != null) {
      impl.retryPolicy(retryPolicy);
    }
    return impl;
  }
}
