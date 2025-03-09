package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.json.JsonObject;
import org.HdrHistogram.Histogram;

/**
 * Circuit breaker metrics.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CircuitBreakerMetrics {
  private final CircuitBreakerImpl circuitBreaker;
  private final String node;

  private final long circuitBreakerResetTimeout;
  private final long circuitBreakerTimeout;

  // Global statistics

  private final RollingWindow rollingWindow;

  CircuitBreakerMetrics(Vertx vertx, CircuitBreakerImpl circuitBreaker, CircuitBreakerOptions options) {
    this.circuitBreaker = circuitBreaker;
    this.circuitBreakerTimeout = circuitBreaker.options().getTimeout();
    this.circuitBreakerResetTimeout = circuitBreaker.options().getResetTimeout();
    this.node = vertx.isClustered() ? ((VertxInternal) vertx).clusterManager().getNodeId() : "local";
    this.rollingWindow = new RollingWindow(options.getMetricsRollingWindow(), options.getMetricsRollingBuckets());
  }

  private synchronized void evictOutdatedOperations() {
    rollingWindow.updateTime();
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
      return (end - begin) / 1_000_000;
    }
  }

  Operation enqueue() {
    return new Operation();
  }

  public synchronized void complete(Operation operation) {
    rollingWindow.add(operation);
  }

  public synchronized JsonObject toJson() {
    JsonObject json = new JsonObject();

    // Configuration
    json.put("resetTimeout", circuitBreakerResetTimeout);
    json.put("timeout", circuitBreakerTimeout);
    json.put("metricRollingWindow", rollingWindow.getMetricRollingWindowSizeInMs());
    json.put("name", circuitBreaker.name());
    json.put("node", node);

    // Current state
    json.put("state", circuitBreaker.state());
    json.put("failures", circuitBreaker.failureCount());

    // Global metrics
    addSummary(json, rollingWindow.totalSummary(), MetricNames.TOTAL);

    // Window metrics
    evictOutdatedOperations();
    addSummary(json, rollingWindow.windowSummary(), MetricNames.ROLLING);

    return json;
  }

  private void addSummary(JsonObject json, RollingWindow.Summary summary, MetricNames names) {
    long calls = summary.count();
    int errorCount = summary.failures + summary.exceptions + summary.timeouts;

    json.put(names.operationCountName, calls - summary.shortCircuited);
    json.put(names.errorCountName, errorCount);
    json.put(names.successCountName, summary.successes);
    json.put(names.timeoutCountName, summary.timeouts);
    json.put(names.exceptionCountName, summary.exceptions);
    json.put(names.failureCountName, summary.failures);

    if (calls == 0) {
      json.put(names.successPercentageName, 0);
      json.put(names.errorPercentageName, 0);
    } else {
      json.put(names.successPercentageName, ((double) summary.successes / calls) * 100);
      json.put(names.errorPercentageName, ((double) (errorCount) / calls) * 100);
    }

    json.put(names.fallbackSuccessCountName, summary.fallbackSuccess);
    json.put(names.fallbackFailureCountName, summary.fallbackFailure);
    json.put(names.shortCircuitedCountName, summary.shortCircuited);

    addLatency(json, summary.statistics, names);
  }


  private void addLatency(JsonObject json, Histogram histogram, MetricNames names) {
    json.put(names.latencyMeanName, histogram.getMean());
    json.put(names.latencyName, new JsonObject()
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

  private enum MetricNames {
    ROLLING("rolling"), TOTAL("total");

    private final String operationCountName;
    private final String errorCountName;
    private final String successCountName;
    private final String timeoutCountName;
    private final String exceptionCountName;
    private final String failureCountName;
    private final String successPercentageName;
    private final String errorPercentageName;
    private final String fallbackSuccessCountName;
    private final String fallbackFailureCountName;
    private final String shortCircuitedCountName;

    private final String latencyMeanName;
    private final String latencyName;

    MetricNames(String prefix){
      operationCountName = prefix + "OperationCount";
      errorCountName = prefix + "ErrorCount";
      successCountName = prefix + "SuccessCount";
      timeoutCountName = prefix + "TimeoutCount";
      exceptionCountName = prefix + "ExceptionCount";
      failureCountName = prefix + "FailureCount";
      successPercentageName = prefix + "SuccessPercentage";
      errorPercentageName = prefix + "ErrorPercentage";
      fallbackSuccessCountName = prefix + "FallbackSuccessCount";
      fallbackFailureCountName = prefix + "FallbackFailureCount";
      shortCircuitedCountName = prefix + "ShortCircuitedCount";

      latencyName = prefix + "Latency";
      latencyMeanName = prefix + "LatencyMean";
    }
  }

  private static class RollingWindow {
    private final Summary history;
    private final Summary[] buckets;
    private final long bucketSizeInNs;

    RollingWindow(long windowSizeInMs, int numberOfBuckets) {
      if (windowSizeInMs % numberOfBuckets != 0) {
        throw new IllegalArgumentException("Window size should be divisible by number of buckets.");
      }
      this.buckets = new Summary[numberOfBuckets];
      for (int i = 0; i < buckets.length; i++) {
        this.buckets[i] = new Summary();
      }
      this.bucketSizeInNs = 1_000_000 * windowSizeInMs / numberOfBuckets;
      this.history = new Summary();
    }

    public void add(Operation operation) {
      getBucket(operation.end).add(operation);
    }

    public Summary totalSummary() {
      Summary total = new Summary();

      total.add(history);
      total.add(windowSummary());

      return total;
    }

    public Summary windowSummary() {
      Summary window = new Summary(buckets[0].bucketIndex);
      for (Summary bucket : buckets) {
        window.add(bucket);
      }

      return window;
    }

    public void updateTime() {
      getBucket(System.nanoTime());
    }

    private Summary getBucket(long timeInNs) {
      long bucketIndex = timeInNs / bucketSizeInNs;

      //sample too old:
      if (bucketIndex < buckets[0].bucketIndex) {
        return history;
      }

      shiftIfNecessary(bucketIndex);

      return buckets[(int) (bucketIndex - buckets[0].bucketIndex)];
    }

    private void shiftIfNecessary(long bucketIndex) {
      long shiftUnlimited = bucketIndex - buckets[buckets.length - 1].bucketIndex;
      if (shiftUnlimited <= 0) {
        return;
      }
      int shift = (int) Long.min(buckets.length, shiftUnlimited);

      // Add old buckets to history
      for(int i = 0; i < shift; i++) {
        history.add(buckets[i]);
      }

      System.arraycopy(buckets, shift, buckets, 0, buckets.length - shift);

      for(int i = buckets.length - shift; i < buckets.length; i++) {
        buckets[i] = new Summary(bucketIndex + i + 1 - buckets.length);
      }
    }

    public long getMetricRollingWindowSizeInMs() {
      return bucketSizeInNs * buckets.length / 1_000_000;
    }

    private static class Summary {
      final long bucketIndex;
      final Histogram statistics;

      private int successes;
      private int failures;
      private int exceptions;
      private int timeouts;
      private int fallbackSuccess;
      private int fallbackFailure;
      private int shortCircuited;

      private Summary() {
        this(-1);
      }

      private Summary(long bucketIndex) {
        this.bucketIndex = bucketIndex;
        statistics = new Histogram(2);
      }

      public void add(Summary other) {
        statistics.add(other.statistics);

        successes += other.successes;
        failures += other.failures;
        exceptions += other.exceptions;
        timeouts += other.timeouts;
        fallbackSuccess += other.fallbackSuccess;
        fallbackFailure += other.fallbackFailure;
        shortCircuited += other.shortCircuited;
      }

      public void add(Operation operation) {
        statistics.recordValue(operation.durationInMs());
        if (operation.complete) {
          successes++;
        } else if (operation.failed) {
          failures++;
        } else if (operation.exception) {
          exceptions++;
        } else if (operation.timeout) {
          timeouts++;
        }

        if (operation.fallbackSucceed) {
          fallbackSuccess++;
        } else if (operation.fallbackFailed) {
          fallbackFailure++;
        }

        if (operation.shortCircuited) {
          shortCircuited++;
        }
      }

      public long count() {
        return statistics.getTotalCount();
      }
    }
  }
}
