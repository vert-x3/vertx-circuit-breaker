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

import io.vertx.circuitbreaker.*;
import io.vertx.core.*;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.json.JsonObject;

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
  private FailurePolicy failurePolicy = FailurePolicy.defaultPolicy();

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

  @Override
  public <T> CircuitBreaker failurePolicy(FailurePolicy<T> failurePolicy) {
    Objects.requireNonNull(failurePolicy);
    this.failurePolicy = failurePolicy;
    return this;
  }

  /**
   * A version of {@link #reset()} that can forcefully change the state to closed even if the circuit breaker is open.
   * <p>
   * This is an internal API.
   *
   * @param force whether we force the state change and allow an illegal transition
   * @return this circuit breaker
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
  public <T> CircuitBreaker executeAndReportWithFallback(Promise<T> resultPromise, Handler<Promise<T>> command,
    Function<Throwable, T> fallback) {

    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();

    CircuitBreakerState currentState;
    synchronized (this) {
      currentState = state;
    }

    CircuitBreakerMetrics.Operation operationMetrics = metrics != null ? metrics.enqueue() : null;

    // this future object tracks the completion of the operation
    // This future is marked as failed on operation failures and timeout.
    Promise<T> operationResult = context.promise();

    if (currentState == CircuitBreakerState.CLOSED) {
      Future<T> opFuture = operationResult.future();
      opFuture.onComplete(new ClosedCircuitCompletion<>(context, resultPromise, fallback, operationMetrics));
      if (options.getMaxRetries() > 0) {
        executeOperation(context, command, retryPromise(context, 0, command, operationResult, operationMetrics), operationMetrics);
      } else {
        executeOperation(context, command, operationResult, operationMetrics);
      }
    } else if (currentState == CircuitBreakerState.OPEN) {
      // Fallback immediately
      if (operationMetrics != null) {
        operationMetrics.shortCircuited();
      }
      invokeFallback(OpenCircuitException.INSTANCE, resultPromise, fallback, operationMetrics);
    } else if (currentState == CircuitBreakerState.HALF_OPEN) {
      if (passed.incrementAndGet() == 1) {
        Future<T> opFuture = operationResult.future();
        opFuture.onComplete(new HalfOpenedCircuitCompletion<>(context, resultPromise, fallback, operationMetrics));
        // Execute the operation
        executeOperation(context, command, operationResult, operationMetrics);
      } else {
        // Not selected, fallback.
        if (operationMetrics != null) {
          operationMetrics.shortCircuited();
        }
        invokeFallback(OpenCircuitException.INSTANCE, resultPromise, fallback, operationMetrics);
      }
    }
    return this;
  }

  private <T> Promise<T> retryPromise(ContextInternal context, int retryCount, Handler<Promise<T>> command,
    Promise<T> operationResult, CircuitBreakerMetrics.Operation operationMetrics) {

    Promise<T> promise = context.promise();
    promise.future().onComplete(event -> {
      if (event.succeeded()) {
        reset();
        operationResult.complete(event.result());
        return;
      }

      CircuitBreakerState currentState;
      synchronized (this) {
        currentState = state;
      }

      if (currentState == CircuitBreakerState.CLOSED) {
        if (retryCount < options.getMaxRetries() - 1) {
          executeRetryWithDelay(event.cause(), retryCount, l -> {
            // Don't report timeout or error in the retry attempt, only the last one.
            executeOperation(context, command, retryPromise(context, retryCount + 1, command, operationResult, null),
              operationMetrics);
          });
        } else {
          executeRetryWithDelay(event.cause(), retryCount, l -> {
            executeOperation(context, command, operationResult, operationMetrics);
          });
        }
      } else {
        operationResult.fail(OpenCircuitException.INSTANCE);
      }
    });
    return promise;
  }

  private void executeRetryWithDelay(Throwable failure, int retryCount, Handler<Void> action) {
    long retryDelay = retryPolicy.delay(failure, retryCount + 1);

    if (retryDelay > 0) {
      vertx.setTimer(retryDelay, l -> {
        action.handle(null);
      });
    } else {
      action.handle(null);
    }
  }

  private <T> void invokeFallback(Throwable reason, Promise<T> resultPromise,
                                  Function<Throwable, T> fallback, CircuitBreakerMetrics.Operation operationMetrics) {
    if (fallback == null) {
      // No fallback, mark the user future as failed.
      resultPromise.fail(reason);
      return;
    }

    try {
      T apply = fallback.apply(reason);
      if (operationMetrics != null) {
        operationMetrics.fallbackSucceed();
      }
      resultPromise.complete(apply);
    } catch (Exception e) {
      resultPromise.fail(e);
      if (operationMetrics != null) {
        operationMetrics.fallbackFailed();
      }
    }
  }

  private <T> void executeOperation(ContextInternal context, Handler<Promise<T>> operation, Promise<T> operationResult,
                                    CircuitBreakerMetrics.Operation operationMetrics) {
    // We use an intermediate future to avoid the passed future to complete or fail after a timeout.
    Promise<T> passedFuture = context.promise();

    // Execute the operation
    if (options.getTimeout() != -1) {
      long timerId = vertx.setTimer(options.getTimeout(), (l) -> {
        // Check if the operation has not already been completed
        if (!operationResult.future().isComplete()) {
          if (operationMetrics != null) {
            operationMetrics.timeout();
          }
          operationResult.fail(TimeoutException.INSTANCE);
        }
        // Else  Operation has completed
      });
      passedFuture.future().onComplete(v -> vertx.cancelTimer(timerId));
    }
    try {
      passedFuture.future().onComplete(ar -> {
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

      operation.handle(passedFuture);
    } catch (Throwable e) {
      if (!operationResult.future().isComplete()) {
        if (operationMetrics != null) {
          operationMetrics.error();
        }
        operationResult.fail(e);
      }
    }
  }

  @Override
  public <T> Future<T> executeWithFallback(Handler<Promise<T>> operation, Function<Throwable, T> fallback) {
    // be careful to not create a new context, to preserve existing (sometimes synchronous) behavior
    ContextInternal context = ContextInternal.current();
    Promise<T> promise = context != null ? context.promise() : Promise.promise();
    executeAndReportWithFallback(promise, operation, fallback);
    return promise.future();
  }

  public <T> Future<T> execute(Handler<Promise<T>> operation) {
    return executeWithFallback(operation, fallback);
  }

  @Override
  public <T> CircuitBreaker executeAndReport(Promise<T> resultPromise, Handler<Promise<T>> operation) {
    return executeAndReportWithFallback(resultPromise, operation, fallback);
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
        // `open()` calls `sendUpdateOnEventBus()`, so no need to repeat it in the previous case
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

  static class RollingCounter {
    // all `RollingCounter` methods are called in a `synchronized (CircuitBreakerImpl.this)` block,
    // which therefore guards access to these fields

    private Map<Long, Long> window;
    private long timeUnitsInWindow;
    private TimeUnit windowTimeUnit;

    public RollingCounter(long timeUnitsInWindow, TimeUnit windowTimeUnit) {
      this.windowTimeUnit = windowTimeUnit;
      this.window = new LinkedHashMap<Long, Long>((int) timeUnitsInWindow + 1) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Long> eldest) {
          return size() > timeUnitsInWindow;
        }
      };
      this.timeUnitsInWindow = timeUnitsInWindow;
    }

    public void increment() {
      long timeSlot = windowTimeUnit.convert(System.currentTimeMillis(), TimeUnit.MILLISECONDS);
      Long current = window.getOrDefault(timeSlot, 0L);
      window.put(timeSlot, ++current);
    }

    public long count() {
      long windowStartTime = windowTimeUnit.convert(System.currentTimeMillis() - windowTimeUnit.toMillis(timeUnitsInWindow), TimeUnit.MILLISECONDS);

      long result = 0;
      for (Map.Entry<Long, Long> entry : window.entrySet()) {
        if (entry.getKey() >= windowStartTime) {
          result += entry.getValue();
        }
      }
      return result;
    }

    public void reset() {
      window.clear();
    }
  }

  @SuppressWarnings("unchecked")
  private abstract class Completion<T> implements Handler<AsyncResult<T>> {

    final Context context;
    final Promise<T> resultFuture;
    final Function<Throwable, T> fallback;
    final CircuitBreakerMetrics.Operation operationMetrics;

    protected Completion(Context context, Promise<T> resultFuture, Function<Throwable, T> fallback, CircuitBreakerMetrics.Operation operationMetrics) {
      this.context = context;
      this.resultFuture = resultFuture;
      this.fallback = fallback;
      this.operationMetrics = operationMetrics;
    }

    @Override
    public void handle(AsyncResult<T> ar) {
      context.runOnContext(v -> {
        if (failurePolicy.test(asFuture(ar))) {
          failureAction();
          if (operationMetrics != null) {
            operationMetrics.failed();
          }
          if (options.isFallbackOnFailure()) {
            invokeFallback(ar.cause(), resultFuture, fallback, operationMetrics);
          } else {
            resultFuture.fail(ar.cause());
          }
        } else {
          if (operationMetrics != null) {
            operationMetrics.complete();
          }
          reset();
          //The event may pass due to a user given predicate. We still want to push up the failure for the user
          //to do any work
          resultFuture.handle(ar);
        }
      });
    }

    private Future<T> asFuture(AsyncResult<T> ar) {
      Future<T> result;
      if (ar instanceof Future) {
        result = (Future<T>) ar;
      } else if (ar.succeeded()) {
        result = Future.succeededFuture(ar.result());
      } else {
        result = Future.failedFuture(ar.cause());
      }
      return result;
    }

    protected abstract void failureAction();
  }

  private class ClosedCircuitCompletion<T> extends Completion<T> {

    ClosedCircuitCompletion(Context context, Promise<T> userFuture, Function<Throwable, T> fallback, CircuitBreakerMetrics.Operation call) {
      super(context, userFuture, fallback, call);
    }

    @Override
    protected void failureAction() {
      incrementFailures();
    }
  }

  private class HalfOpenedCircuitCompletion<T> extends Completion<T> {

    HalfOpenedCircuitCompletion(Context context, Promise<T> userFuture, Function<Throwable, T> fallback, CircuitBreakerMetrics.Operation call) {
      super(context, userFuture, fallback, call);
    }

    @Override
    protected void failureAction() {
      open();
    }
  }
}
