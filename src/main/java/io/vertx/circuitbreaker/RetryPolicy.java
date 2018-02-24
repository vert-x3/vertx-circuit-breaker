package io.vertx.circuitbreaker;

import io.vertx.codegen.annotations.VertxGen;

@VertxGen
@FunctionalInterface
public interface RetryPolicy {
  /**
   * Calculate and return timeout before next retry
   * @param retryCount 1-based count of the current retry
   * @return timeout value in milliseconds
   */
  long getTimeout(int retryCount);
}
