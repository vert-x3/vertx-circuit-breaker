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
import io.vertx.circuitbreaker.OpenCircuitException;
import io.vertx.circuitbreaker.RetryPolicy;
import io.vertx.circuitbreaker.TimeoutException;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.json.JsonObject;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
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
  private RollingCounter rollingFailures;

  private final AtomicInteger passed = new AtomicInteger();

  private final CircuitBreakerMetrics metrics;
  private RetryPolicy retryPolicy = (failure, retryCount) -> 0L;

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

    this.rollingFailures = new RollingCounter(this.options.getFailuresRollingWindow() / 1000, TimeUnit.SECONDS);

    if (this.options.getNotificationAddress() != null) {
      this.metrics = new CircuitBreakerMetrics(vertx, this, this.options);
      sendUpdateOnEventBus();
      if (this.options.getNotificationPeriod() > 0) {
        this.periodicUpdateTask = vertx.setPeriodic(this.options.getNotificationPeriod(), l -> sendUpdateOnEventBus());
      } else {
        this.periodicUpdateTask = -1;
      }
    } else {
      this.metrics = null;
      this.periodicUpdateTask = -1;
    }
  }

  @Override
  public CircuitBreaker close() {
    if (metrics != null) {
      if (periodicUpdateTask != -1) {
        vertx.cancelTimer(periodicUpdateTask);
      }
      metrics.close();
    }
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

  /**
   * A version of reset that can force the the state to `close` even if the circuit breaker is open. This is an
   * internal API.
   *
   * @param force whether or not we force the state and allow an illegal transition
   * @return the current circuit breaker.
   */
  public synchronized CircuitBreaker reset(boolean force) {
    rollingFailures.reset();

    if (state == CircuitBreakerState.CLOSED) {
      // Do nothing else.
      return this;
    }

    if (!force && state == CircuitBreakerState.OPEN) {
      // Resetting the circuit breaker while we are in the open state is an illegal transition
      return this;
    }

    state = CircuitBreakerState.CLOSED;
    closeHandler.handle(null);
    sendUpdateOnEventBus();
    return this;
  }

  @Override
  public synchronized CircuitBreaker reset() {
    return reset(false);
  }

  private synchronized void sendUpdateOnEventBus() {
    if (metrics != null) {
      DeliveryOptions deliveryOptions = new DeliveryOptions()
        .setLocalOnly(options.isNotificationLocalOnly());
      vertx.eventBus().publish(options.getNotificationAddress(), metrics.toJson(), deliveryOptions);
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
    return rollingFailures.count();
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
    Promise<T> userFuture,
    Handler<Promise<T>> command,
    Function<Throwable, T> fallback) {

    Context context = vertx.getOrCreateContext();

    CircuitBreakerState currentState;
    synchronized (this) {
      currentState = state;
    }

    CircuitBreakerMetrics.Operation call = metrics != null ? metrics.enqueue() : null;

    // this future object tracks the completion of the operation
    // This future is marked as failed on operation failures and timeout.
    Promise<T> operationResult = Promise.promise();

    if (currentState == CircuitBreakerState.CLOSED) {
      operationResult.future().onComplete(event -> {
        context.runOnContext(v -> {
          if (event.failed()) {
            incrementFailures();
            if (call != null) {
              call.failed();
            }
            if (options.isFallbackOnFailure()) {
              invokeFallback(event.cause(), userFuture, fallback, call);
            } else {
              userFuture.fail(event.cause());
            }
          } else {
            if (call != null) {
              call.complete();
            }
            reset();
            userFuture.complete(event.result());
          }
          // Else the operation has been canceled because of a time out.
        });
      });

      if (options.getMaxRetries() > 0) {
        executeOperation(context, command, retryFuture(context, 0, command, operationResult, call), call);
      } else {
        executeOperation(context, command, operationResult, call);
      }
    } else if (currentState == CircuitBreakerState.OPEN) {
      // Fallback immediately
      if (call != null) {
        call.shortCircuited();
      }
      invokeFallback(OpenCircuitException.INSTANCE, userFuture, fallback, call);
    } else if (currentState == CircuitBreakerState.HALF_OPEN) {
      if (passed.incrementAndGet() == 1) {
        operationResult.future().onComplete(event -> {
          context.runOnContext(v -> {
            if (event.failed()) {
              open();
              if (call != null) {
                call.failed();
              }
              if (options.isFallbackOnFailure()) {
                invokeFallback(event.cause(), userFuture, fallback, call);
              } else {
                userFuture.fail(event.cause());
              }
            } else {
              if (call != null) {
                call.complete();
              }
              reset();
              userFuture.complete(event.result());
            }
          });
        });
        // Execute the operation
        executeOperation(context, command, operationResult, call);
      } else {
        // Not selected, fallback.
        if (call != null) {
          call.shortCircuited();
        }
        invokeFallback(OpenCircuitException.INSTANCE, userFuture, fallback, call);
      }
    }
    return this;
  }

  private <T> Promise<T> retryFuture(Context context, int retryCount, Handler<Promise<T>> command, Promise<T>
    operationResult, CircuitBreakerMetrics.Operation call) {
    Promise<T> retry = Promise.promise();

    retry.future().onComplete(event -> {
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
          executeRetryWithTimeout(event.cause(), retryCount, l -> {
            context.runOnContext(v -> {
              // Don't report timeout or error in the retry attempt, only the last one.
              executeOperation(context, command, retryFuture(context, retryCount + 1, command, operationResult, null),
                call);
            });
          });
        } else {
          executeRetryWithTimeout(event.cause(), retryCount, (l) -> {
            context.runOnContext(v -> {
              executeOperation(context, command, operationResult, call);
            });
          });
        }
      } else {
        context.runOnContext(v -> operationResult.fail(OpenCircuitException.INSTANCE));
      }
    });
    return retry;
  }

  private void executeRetryWithTimeout(Throwable failure, int retryCount, Handler<Void> action) {
    long retryTimeout = retryPolicy.delay(failure, retryCount + 1);

    if (retryTimeout > 0) {
      vertx.setTimer(retryTimeout, (l) -> {
        action.handle(null);
      });
    } else {
      action.handle(null);
    }
  }

  private <T> void invokeFallback(Throwable reason, Promise<T> userFuture,
                                  Function<Throwable, T> fallback, CircuitBreakerMetrics.Operation operation) {
    if (fallback == null) {
      // No fallback, mark the user future as failed.
      userFuture.fail(reason);
      return;
    }

    try {
      T apply = fallback.apply(reason);
      if (operation != null) {
        operation.fallbackSucceed();
      }
      userFuture.complete(apply);
    } catch (Exception e) {
      userFuture.fail(e);
      if (operation != null) {
        operation.fallbackFailed();
      }
    }
  }

  private <T> void executeOperation(Context context, Handler<Promise<T>> operation, Promise<T> operationResult,
                                    CircuitBreakerMetrics.Operation call) {
    // We use an intermediate future to avoid the passed future to complete or fail after a timeout.
    Promise<T> passedFuture = Promise.promise();

    // Execute the operation
    if (options.getTimeout() != -1) {
      long timerId = vertx.setTimer(options.getTimeout(), (l) -> {
        context.runOnContext(v -> {
          // Check if the operation has not already been completed
          if (!operationResult.future().isComplete()) {
            if (call != null) {
              call.timeout();
            }
            operationResult.fail(TimeoutException.INSTANCE);
          }
          // Else  Operation has completed
        });
      });
      passedFuture.future().onComplete(v -> vertx.cancelTimer(timerId));
    }
    try {
      passedFuture.future().onComplete(ar -> {
        context.runOnContext(v -> {
          if (ar.failed()) {
            if (!operationResult.future().isComplete()) {
              operationResult.fail(ar.cause());
            }
          } else {
            if (!operationResult.future().isComplete()) {
              operationResult.complete(ar.result());
            }
          }
        });
      });

      operation.handle(passedFuture);
    } catch (Throwable e) {
      context.runOnContext(v -> {
        if (!operationResult.future().isComplete()) {
          if (call != null) {
            call.error();
          }
          operationResult.fail(e);
        }
      });
    }
  }

  @Override
  public <T> Future<T> executeWithFallback(Handler<Promise<T>> operation, Function<Throwable, T> fallback) {
    Promise<T> future = Promise.promise();
    executeAndReportWithFallback(future, operation, fallback);
    return future.future();
  }

  public <T> Future<T> execute(Handler<Promise<T>> operation) {
    return executeWithFallback(operation, fallback);
  }

  @Override
  public <T> CircuitBreaker executeAndReport(Promise<T> resultFuture, Handler<Promise<T>> operation) {
    return executeAndReportWithFallback(resultFuture, operation, fallback);
  }

  @Override
  public String name() {
    return name;
  }

  private synchronized void incrementFailures() {
    rollingFailures.increment();
    if (rollingFailures.count() >= options.getMaxFailures()) {
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
  public JsonObject getMetrics() {
    return metrics.toJson();
  }

  public CircuitBreakerOptions options() {
    return options;
  }

  @Override
  public CircuitBreaker retryPolicy(Function<Integer, Long> retryPolicy) {
    this.retryPolicy = (failure, retryCount) -> retryPolicy.apply(retryCount);
    return this;
  }

  @Override
  public CircuitBreaker retryPolicy(RetryPolicy retryPolicy) {
    this.retryPolicy = retryPolicy;
    return this;
  }

  public static class RollingCounter {
    private Map<Long, Long> window;
    private long timeUnitsInWindow;
    private TimeUnit windowTimeUnit;

    public RollingCounter(long timeUnitsInWindow, TimeUnit windowTimeUnit) {
      this.windowTimeUnit = windowTimeUnit;
      this.window = new LinkedHashMap<>((int) timeUnitsInWindow + 1);
      this.timeUnitsInWindow = timeUnitsInWindow;
    }

    public void increment() {
      long timeSlot = windowTimeUnit.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
      Long current = window.getOrDefault(timeSlot, 0L);
      window.put(timeSlot, ++current);

      if (window.size() > timeUnitsInWindow) {
        Iterator<Long> iterator = window.keySet().iterator();
        if (iterator.hasNext()) {
          window.remove(iterator.next());
        }
      }
    }

    public long count() {
      long windowStartTime = windowTimeUnit.convert(System.currentTimeMillis() - windowTimeUnit.toMillis(timeUnitsInWindow), TimeUnit.MILLISECONDS);
      return window.entrySet().stream().filter(entry -> entry.getKey() >= windowStartTime).mapToLong(entry -> entry.getValue()).sum();
    }

    public void reset() {
      window.clear();
    }
  }
}
