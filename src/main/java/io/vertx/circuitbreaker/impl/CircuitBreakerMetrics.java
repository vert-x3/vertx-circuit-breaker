package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CircuitBreakerMetrics {
  private final long rollingWindow;
  private final Vertx vertx;
  private final long timer;
  private final CircuitBreaker circuitBreaker;
  private final String node;

  // Global statistics

  private int calls = 0;
  private int failures = 0;
  private int success = 0;
  private int timeout = 0;
  private int exceptions = 0;

  private List<Operation> window = new ArrayList<>();

  private Statistics statistics = new Statistics();

  CircuitBreakerMetrics(Vertx vertx, CircuitBreaker circuitBreaker, CircuitBreakerOptions options) {
    this.vertx = vertx;
    this.circuitBreaker = circuitBreaker;
    rollingWindow = options.getMetricsRollingWindow();
    timer = vertx.setPeriodic(rollingWindow, l -> evictOutdatedCalls());
    node = vertx.isClustered() ? ((VertxInternal) vertx).getClusterManager().getNodeID() : "local";
  }

  private synchronized void evictOutdatedCalls() {
    long beginningOfTheWindow = System.currentTimeMillis() - rollingWindow;
    List<Operation> toRemove = window.stream()
        .filter(operation -> operation.begin < beginningOfTheWindow)
        .collect(Collectors.toList());
    System.out.println("Removing " + toRemove.size() + " calls - out of window");
    window.removeAll(toRemove);
  }

  public void close() {
    vertx.cancelTimer(timer);
  }

  class Operation {
    final long begin;
    private long end;
    private boolean complete;
    private boolean failed;
    private boolean timeout;
    private boolean exception;
    private boolean fallbackFailed;
    private boolean fallbackSucceed;
    private boolean shortCircuited;

    Operation() {
      begin = System.currentTimeMillis();
    }

    synchronized void complete() {
      end = System.currentTimeMillis();
      complete = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    synchronized void failed() {
      if (timeout  || exception) {
        // Already completed.
        return;
      }
      end = System.currentTimeMillis();
      failed = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    synchronized void timeout() {
      end = System.currentTimeMillis();
      failed = false;
      timeout = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    synchronized void error() {
      end = System.currentTimeMillis();
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
      end = System.currentTimeMillis();
      shortCircuited = true;
      CircuitBreakerMetrics.this.complete(this);
    }

    @Override
    public synchronized String toString() {
      String content = "{";
      if (exception) {
        content += "exception:true,";
      }
      if (failed) {
        content += "failed:true,";
      }
      if (timeout) {
        content += "timeout:true,";
      }
      if (complete) {
        content += "complete:true,";
      }
      if (shortCircuited) {
        content += "shortCircuited:true,";
      }
      content += "}";
      return content;
    }
  }

  Operation enqueue() {
    return new Operation();
  }

  public synchronized void complete(Operation operation) {
    window.add(operation);

    // Compute global statistics
    long duration = operation.end - operation.begin;
    statistics.add(duration);
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

  synchronized JsonObject toJson() {
    JsonObject json = new JsonObject();

    json.put("name", circuitBreaker.name());

    // Current state
    json.put("state", circuitBreaker.state());
    json.put("failures", circuitBreaker.failureCount());
    json.put("node", node);

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

    json.put("totalLatencyMean", statistics.getMean());
    json.put("totalLatency", new JsonObject()
        .put("0", statistics.getValue(0.0))
        .put("25", statistics.getValue(0.25))
        .put("50", statistics.getMedian())
        .put("75", statistics.get75thPercentile())
        .put("90", statistics.getValue(0.90))
        .put("95", statistics.get95thPercentile())
        .put("99", statistics.get99thPercentile())
        .put("99.5", statistics.getValue(0.995))
        .put("100", statistics.getValue(1)));

    // Window metrics
    int rollingException = 0;
    int rollingFailure = 0;
    int rollingSuccess = 0;
    int rollingTimeout = 0;
    int rollingFallbackSuccess = 0;
    int rollingFallbackFailure = 0;
    int rollingShortCircuited = 0;
    Statistics rollingStatistic = new Statistics();
    for (Operation op : window) {
      rollingStatistic.add(op.end - op.begin);
      if (op.complete) {
        rollingSuccess = rollingSuccess + 1;
      } else if (op.failed) {
        rollingFailure = rollingFailure + 1;
      } else if (op.exception) {
        rollingException = rollingException + 1;
      } else if (op.timeout){
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

    json.put("rollingOperationCount", window.size());
    json.put("rollingErrorCount", rollingException + rollingFailure + rollingTimeout);
    json.put("rollingSuccessCount", rollingSuccess);
    json.put("rollingTimeoutCount", rollingTimeout);
    json.put("rollingExceptionCount", rollingException);
    json.put("rollingFailureCount", rollingFailure);
    if (calls == 0) {
      json.put("rollingSuccessPercentage", 0);
      json.put("rollingErrorPercentage", 0);
    } else {
      json.put("rollingSuccessPercentage", ((double) rollingSuccess / calls) * 100);
      json.put("rollingErrorPercentage",
          ((double) (rollingException + rollingFailure + rollingTimeout) / calls) * 100);

    }

    json.put("rollingFallbackSuccessCount", rollingFallbackSuccess);
    json.put("rollingFallbackFailureCount", rollingFallbackFailure);
    json.put("rollingShortCircuitedCount", rollingShortCircuited);

    json.put("rollingLatencyMean", rollingStatistic.getMean());
    json.put("rollingLatency", new JsonObject()
        .put("0", rollingStatistic.getValue(0.0))
        .put("25", rollingStatistic.getValue(0.25))
        .put("50", rollingStatistic.getMedian())
        .put("75", rollingStatistic.get75thPercentile())
        .put("90", rollingStatistic.getValue(0.90))
        .put("95", rollingStatistic.get95thPercentile())
        .put("99", rollingStatistic.get99thPercentile())
        .put("99.5", rollingStatistic.getValue(0.995))
        .put("100", rollingStatistic.getValue(1)));

    return json;
  }
}
