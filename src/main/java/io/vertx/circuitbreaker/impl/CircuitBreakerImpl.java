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
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CircuitBreakerImpl implements CircuitBreaker {

  private static final Handler<Void> NOOP = (v) -> {
    // Nothing...
  };

  private final Vertx vertx;
  private final CircuitBreakerOptions options;
  private final String name;
  private final long periodicUpdateTask;

  private Handler<Void> openHandler = NOOP;
  private Handler<Void> halfOpenHandler = NOOP;
  private Handler<Void> closeHandler = NOOP;
  private Function fallback = null;

  private CircuitBreakerState state = CircuitBreakerState.CLOSED;
  private long failures = 0;
  private final AtomicInteger passed = new AtomicInteger();

  private CircuitBreakerMetrics metrics;

  public CircuitBreakerImpl(String name, Vertx vertx, CircuitBreakerOptions options) {
    Objects.requireNonNull(name);
    Objects.requireNonNull(vertx);
    this.vertx = vertx;
    this.name = name;

    if (options == null) {
      this.options = new CircuitBreakerOptions();
    } else {
      this.options = new CircuitBreakerOptions(options);
    }

    this.metrics = new CircuitBreakerMetrics(vertx, this, options);

    sendUpdateOnEventBus();

    if (this.options.getNotificationPeriod() > 0) {
      this.periodicUpdateTask = vertx.setPeriodic(this.options.getNotificationPeriod(), l -> sendUpdateOnEventBus());
    } else {
      this.periodicUpdateTask = -1;
    }
  }

  @Override
  public CircuitBreaker close() {
    if (this.periodicUpdateTask != -1) {
      vertx.cancelTimer(this.periodicUpdateTask);
    }
    metrics.close();
    return this;
  }

  @Override
  public synchronized CircuitBreaker openHandler(Handler<Void> handler) {
    Objects.requireNonNull(handler);
    openHandler = handler;
    return this;
  }

  @Override
  public synchronized CircuitBreaker halfOpenHandler(Handler<Void> handler) {
    Objects.requireNonNull(handler);
    halfOpenHandler = handler;
    return this;
  }

  @Override
  public synchronized CircuitBreaker closeHandler(Handler<Void> handler) {
    Objects.requireNonNull(handler);
    closeHandler = handler;
    return this;
  }

  @Override
  public <T> CircuitBreaker fallback(Function<Throwable, T> handler) {
    Objects.requireNonNull(handler);
    fallback = handler;
    return this;
  }

  @Override
  public synchronized CircuitBreaker reset() {
    failures = 0;

    if (state == CircuitBreakerState.CLOSED) {
      // Do nothing else.
      return this;
    }

    state = CircuitBreakerState.CLOSED;
    closeHandler.handle(null);
    sendUpdateOnEventBus();
    return this;
  }

  private synchronized void sendUpdateOnEventBus() {
    String address = options.getNotificationAddress();
    if (address != null) {
      vertx.eventBus().publish(address, metrics.toJson());
    }
  }

  @Override
  public synchronized CircuitBreaker open() {
    state = CircuitBreakerState.OPEN;
    openHandler.handle(null);
    sendUpdateOnEventBus();

    // Set up the attempt reset timer
    long period = options.getResetTimeout();
    if (period != -1) {
      vertx.setTimer(period, l -> attemptReset());
    }

    return this;
  }

  @Override
  public synchronized long failureCount() {
    return failures;
  }

  @Override
  public synchronized CircuitBreakerState state() {
    return state;
  }

  private synchronized CircuitBreaker attemptReset() {
    if (state == CircuitBreakerState.OPEN) {
      passed.set(0);
      state = CircuitBreakerState.HALF_OPEN;
      halfOpenHandler.handle(null);
      sendUpdateOnEventBus();
    }
    return this;
  }

  @Override
  public <T> CircuitBreaker executeAndReportWithFallback(
    Future<T> userFuture,
    Handler<Future<T>> command,
    Function<Throwable, T> fallback) {

    Context context = vertx.getOrCreateContext();

    CircuitBreakerState currentState;
    synchronized (this) {
      currentState = state;
    }

    CircuitBreakerMetrics.Operation call = metrics.enqueue();

    // this future object tracks the completion of the operation
    // This future is marked as failed on operation failures and timeout.
    Future<T> operationResult = Future.future();
    operationResult.setHandler(event -> {
      context.runOnContext(v -> {
        if (event.failed()) {
          incrementFailures();
          call.failed();
          if (options.isFallbackOnFailure()) {
            invokeFallback(event.cause(), userFuture, fallback, call);
          } else {
            userFuture.fail(event.cause());
          }
        } else {
          call.complete();
          reset();
          userFuture.complete(event.result());
        }
        // Else the operation has been canceled because of a time out.
      });

    });

    if (currentState == CircuitBreakerState.CLOSED) {
      if (options.getMaxRetries() > 0) {
        executeOperation(context, command, retryFuture(context, 1, command, operationResult, call), call);
      } else {
        executeOperation(context, command, operationResult, call);
      }
    } else if (currentState == CircuitBreakerState.OPEN) {
      // Fallback immediately
      call.shortCircuited();
      invokeFallback(new RuntimeException("open circuit"), userFuture, fallback, call);
    } else if (currentState == CircuitBreakerState.HALF_OPEN) {
      if (passed.incrementAndGet() == 1) {
        operationResult.setHandler(event -> {
          if (event.failed()) {
            open();
            if (options.isFallbackOnFailure()) {
              invokeFallback(event.cause(), userFuture, fallback, call);
            } else {
              userFuture.fail(event.cause());
            }
          } else {
            reset();
            userFuture.complete(event.result());
          }
        });
        // Execute the operation
        executeOperation(context, command, operationResult, call);
      } else {
        // Not selected, fallback.
        call.shortCircuited();
        invokeFallback(new RuntimeException("open circuit"), userFuture, fallback, call);
      }
    }
    return this;
  }

  private <T> Future<T> retryFuture(Context context, int retryCount, Handler<Future<T>> command, Future<T>
    operationResult, CircuitBreakerMetrics.Operation call) {
    Future<T> retry = Future.future();

    retry.setHandler(event -> {
      if (event.succeeded()) {
        reset();
        context.runOnContext(v -> {
          operationResult.complete(event.result());
        });
        return;
      }

      CircuitBreakerState currentState;
      synchronized (this) {
        currentState = state;
      }

      if (currentState == CircuitBreakerState.CLOSED) {
        if (retryCount < options.getMaxRetries() - 1) {
          context.runOnContext(v -> {
            // Don't report timeout or error in the retry attempt, only the last one.
            executeOperation(context, command, retryFuture(context, retryCount + 1, command, operationResult, null),
              call);
          });

        } else {
          context.runOnContext(v -> {
            executeOperation(context, command, operationResult, call);
          });
        }
      } else {
        context.runOnContext(v -> {
          operationResult.fail(new RuntimeException("open circuit"));
        });
      }
    });
    return retry;
  }

  private <T> void invokeFallback(Throwable reason, Future<T> userFuture,
                                  Function<Throwable, T> fallback, CircuitBreakerMetrics.Operation operation) {
    if (fallback == null) {
      // No fallback, mark the user future as failed.
      userFuture.fail(reason);
      return;
    }

    try {
      T apply = fallback.apply(reason);
      operation.fallbackSucceed();
      userFuture.complete(apply);
    } catch (Exception e) {
      userFuture.fail(e);
      operation.fallbackFailed();
    }
  }

  private <T> void executeOperation(Context context, Handler<Future<T>> operation, Future<T> operationResult,
                                    CircuitBreakerMetrics.Operation call) {
    // Execute the operation
    if (options.getTimeout() != -1) {
      vertx.setTimer(options.getTimeout(), (l) -> {
        context.runOnContext(v -> {
          // Check if the operation has not already been completed
          if (!operationResult.isComplete()) {
            if (call != null) {
              call.timeout();
            }
            operationResult.fail("operation timeout");
          }
          // Else  Operation has completed
        });
      });
    }
    try {
      // We use an intermediate future to avoid the passed future to complete or fail after a timeout.
      Future<T> passedFuture = Future.future();
      passedFuture.setHandler(ar -> {
        context.runOnContext(v -> {
          if (ar.failed()) {
            if (!operationResult.isComplete()) {
              operationResult.fail(ar.cause());
            }
          } else {
            if (!operationResult.isComplete()) {
              operationResult.complete(ar.result());
            }
          }
        });
      });

      operation.handle(passedFuture);
    } catch (Throwable e) {
      context.runOnContext(v -> {
        if (!operationResult.isComplete()) {
          if (call != null) {
            call.error();
          }
          operationResult.fail(e);
        }
      });
    }
  }

  @Override
  public <T> Future<T> executeWithFallback(Handler<Future<T>> operation, Function<Throwable, T> fallback) {
    Future<T> future = Future.future();
    executeAndReportWithFallback(future, operation, fallback);
    return future;
  }

  public <T> Future<T> execute(Handler<Future<T>> operation) {
    return executeWithFallback(operation, fallback);
  }

  @Override
  public <T> CircuitBreaker executeAndReport(Future<T> resultFuture, Handler<Future<T>> operation) {
    return executeAndReportWithFallback(resultFuture, operation, fallback);
  }

  @Override
  public String name() {
    return name;
  }

  private synchronized void incrementFailures() {
    failures++;
    if (failures >= options.getMaxFailures()) {
      if (state != CircuitBreakerState.OPEN) {
        open();
      } else {
        // No need to do it in the previous case, open() do it.
        // If open has been called, no need to send update, it will be done by the `open` method.
        sendUpdateOnEventBus();
      }
    } else {
      // Number of failure has changed, send update.
      sendUpdateOnEventBus();
    }
  }

  /**
   * For testing purpose only.
   *
   * @return retrieve the metrics.
   */
  public CircuitBreakerMetrics getMetrics() {
    return metrics;
  }

  public CircuitBreakerOptions options() {
    return options;
  }
}
