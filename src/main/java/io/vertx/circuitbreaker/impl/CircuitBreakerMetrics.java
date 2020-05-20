package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
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
    this.node = vertx.isClustered() ? ((VertxInternal) vertx).getClusterManager().getNodeId() : "local";
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
    addSummary(json, rollingWindow.totalSummary(), "total");

    // Window metrics
    evictOutdatedOperations();
    addSummary(json, rollingWindow.windowSummary(), "rolling");

    return json;
  }

  private void addSummary(JsonObject json, RollingWindow.Summary summary, String prefix) {
    long calls = summary.count();
    int errorCount = summary.failures + summary.exceptions + summary.timeouts;

    json.put(prefix + "OperationCount", calls - summary.shortCircuited);
    json.put(prefix + "ErrorCount", errorCount);
    json.put(prefix + "SuccessCount", summary.successes);
    json.put(prefix + "TimeoutCount", summary.timeouts);
    json.put(prefix + "ExceptionCount", summary.exceptions);
    json.put(prefix + "FailureCount", summary.failures);

    if (calls == 0) {
      json.put(prefix + "SuccessPercentage", 0);
      json.put(prefix + "ErrorPercentage", 0);
    } else {
      json.put(prefix + "SuccessPercentage", ((double) summary.successes / calls) * 100);
      json.put(prefix + "ErrorPercentage", ((double) (errorCount) / calls) * 100);
    }

    json.put(prefix + "FallbackSuccessCount", summary.fallbackSuccess);
    json.put(prefix + "FallbackFailureCount", summary.fallbackFailure);
    json.put(prefix + "ShortCircuitedCount", summary.shortCircuited);

    addLatency(json, summary.statistics, prefix);
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
        fallbackSuccess += other.fallbackSuccess ;
        fallbackFailure += other.fallbackFailure ;
        shortCircuited += other.shortCircuited ;
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
