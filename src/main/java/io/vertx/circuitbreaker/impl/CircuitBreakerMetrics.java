package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import org.HdrHistogram.Histogram;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Circuit breaker metrics.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CircuitBreakerMetrics {
  private final long rollingWindow;
  private final CircuitBreakerImpl circuitBreaker;
  private final String node;

  private final long circuitBreakerResetTimeout;
  private final long circuitBreakerTimeout;
  private final long windowPeriodInNs;

  // Global statistics

  private int calls = 0;
  private int failures = 0;
  private int success = 0;
  private int timeout = 0;
  private int exceptions = 0;

  private List<Operation> window = new ArrayList<>();

  private Histogram statistics = new Histogram(3);

  CircuitBreakerMetrics(Vertx vertx, CircuitBreakerImpl circuitBreaker, CircuitBreakerOptions options) {
    this.circuitBreaker = circuitBreaker;
    this.circuitBreakerTimeout = circuitBreaker.options().getTimeout();
    this.circuitBreakerResetTimeout = circuitBreaker.options().getResetTimeout();
    this.rollingWindow = options.getMetricsRollingWindow();
    this.node = vertx.isClustered() ? ((VertxInternal) vertx).getClusterManager().getNodeID() : "local";
    this.windowPeriodInNs =  rollingWindow * 1000000;
  }

  private synchronized List<Operation> evictOutdatedOperations() {
    // IMPORTANT: operation.begin is in nanosecond.
    long beginningOfTheWindow = System.nanoTime() - windowPeriodInNs;
    // Remove the current element from the iterator and the list.
    window.removeIf(operation -> operation.begin < beginningOfTheWindow);
    return window;
  }

  public void close() {
    // do nothing by default.
  }

  class Operation {
    final long begin;
    private volatile long end;
    private boolean complete;
    private boolean failed;
    private boolean timeout;
    private boolean exception;
    private boolean fallbackFailed;
    private boolean fallbackSucceed;
    private boolean shortCircuited;

    Operation() {
      begin = System.nanoTime();
    }

    synchronized void complete() {
      end = System.nanoTime();
      complete = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    synchronized void failed() {
      if (timeout || exception) {
        // Already completed.
        return;
      }
      end = System.nanoTime();
      failed = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    synchronized void timeout() {
      end = System.nanoTime();
      failed = false;
      timeout = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    synchronized void error() {
      end = System.nanoTime();
      failed = false;
      exception = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    synchronized void fallbackFailed() {
      fallbackFailed = true;
    }

    synchronized void fallbackSucceed() {
      fallbackSucceed = true;
    }

    synchronized void shortCircuited() {
      end = System.nanoTime();
      shortCircuited = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    long durationInMs() {
      return (end - begin) / 1000000;
    }
  }

  Operation enqueue() {
    return new Operation();
  }

  public synchronized void complete(Operation operation) {
    window.add(operation);

    // Compute global statistics
    statistics.recordValue(operation.durationInMs());
    calls++;
    if (operation.exception) {
      exceptions++;
    } else if (operation.complete) {
      success++;
    } else if (operation.timeout) {
      timeout++;
    } else if (operation.failed) {
      failures++;
    }
  }

  public synchronized JsonObject toJson() {
    JsonObject json = new JsonObject();

    // Configuration
    json.put("resetTimeout", circuitBreakerResetTimeout);
    json.put("timeout", circuitBreakerTimeout);
    json.put("metricRollingWindow", rollingWindow);
    json.put("name", circuitBreaker.name());
    json.put("node", node);

    // Current state
    json.put("state", circuitBreaker.state());
    json.put("failures", circuitBreaker.failureCount());

    // Global metrics
    json.put("totalErrorCount", failures + exceptions + timeout);
    json.put("totalSuccessCount", success);
    json.put("totalTimeoutCount", timeout);
    json.put("totalExceptionCount", exceptions);
    json.put("totalFailureCount", failures);
    json.put("totalOperationCount", calls);
    if (calls == 0) {
      json.put("totalSuccessPercentage", 0);
      json.put("totalErrorPercentage", 0);
    } else {
      json.put("totalSuccessPercentage", ((double) success / calls) * 100);
      json.put("totalErrorPercentage", ((double) (failures + exceptions + timeout) / calls) * 100);
    }

    addLatency(json, statistics, "total");

    // Window metrics
    evictOutdatedOperations();
    int rollingException = 0;
    int rollingFailure = 0;
    int rollingSuccess = 0;
    int rollingTimeout = 0;
    int rollingFallbackSuccess = 0;
    int rollingFallbackFailure = 0;
    int rollingShortCircuited = 0;
    Histogram rollingStatistic = new Histogram(3);
    for (Operation op : window) {
      rollingStatistic.recordValue(op.durationInMs());
      if (op.complete) {
        rollingSuccess = rollingSuccess + 1;
      } else if (op.failed) {
        rollingFailure = rollingFailure + 1;
      } else if (op.exception) {
        rollingException = rollingException + 1;
      } else if (op.timeout) {
        rollingTimeout = rollingTimeout + 1;
      }

      if (op.fallbackSucceed) {
        rollingFallbackSuccess++;
      } else if (op.fallbackFailed) {
        rollingFallbackFailure++;
      }

      if (op.shortCircuited) {
        rollingShortCircuited++;
      }
    }

    json.put("rollingOperationCount", window.size() - rollingShortCircuited);
    json.put("rollingErrorCount", rollingException + rollingFailure + rollingTimeout);
    json.put("rollingSuccessCount", rollingSuccess);
    json.put("rollingTimeoutCount", rollingTimeout);
    json.put("rollingExceptionCount", rollingException);
    json.put("rollingFailureCount", rollingFailure);
    if (window.size() == 0) {
      json.put("rollingSuccessPercentage", 0);
      json.put("rollingErrorPercentage", 0);
    } else {
      json.put("rollingSuccessPercentage", ((double) rollingSuccess / window.size()) * 100);
      json.put("rollingErrorPercentage",
        ((double) (rollingException + rollingFailure + rollingTimeout + rollingShortCircuited) / window.size()) * 100);
    }

    json.put("rollingFallbackSuccessCount", rollingFallbackSuccess);
    json.put("rollingFallbackFailureCount", rollingFallbackFailure);
    json.put("rollingShortCircuitedCount", rollingShortCircuited);

    addLatency(json, rollingStatistic, "rolling");
    return json;
  }


  private void addLatency(JsonObject json, Histogram histogram, String prefix) {
    json.put(prefix + "LatencyMean", histogram.getMean());
    json.put(prefix + "Latency", new JsonObject()
      .put("0", histogram.getValueAtPercentile(0))
      .put("25", histogram.getValueAtPercentile(25))
      .put("50", histogram.getValueAtPercentile(50))
      .put("75", histogram.getValueAtPercentile(75))
      .put("90", histogram.getValueAtPercentile(90))
      .put("95", histogram.getValueAtPercentile(95))
      .put("99", histogram.getValueAtPercentile(99))
      .put("99.5", histogram.getValueAtPercentile(99.5))
      .put("100", histogram.getValueAtPercentile(100)));
  }
}
