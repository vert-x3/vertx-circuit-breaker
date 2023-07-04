package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.RetryPolicy;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;

/**
 * Checks that retry policy is being applied
 */
public class RetryPolicyTest {
  private Vertx vertx;

  @Before
  public void setup() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() {
    vertx.close();
  }

  @Test
  public void testWithRetryPolicy() {
    runRetryPolicyTest(RetryPolicy.linearDelay(100, 10000));
  }

  @Test
  public void testWithZeroRetryPolicy() {
    runRetryPolicyTest((failure, retryCount) -> 0);
  }

  @Test
  public void testWithNegativeRetryPolicy() {
    runRetryPolicyTest((failure, retryCount) -> -1);
  }

  /**
   * Helper method to run retry policy tests
   */
  private void runRetryPolicyTest(RetryPolicy retryPolicy) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
      new CircuitBreakerOptions().setMaxFailures(5).setMaxRetries(5));
    AtomicInteger counter = new AtomicInteger();
    AtomicInteger retryPolicyCounter = new AtomicInteger();

    breaker.retryPolicy((failure, retryCount) -> {
      retryPolicyCounter.incrementAndGet();
      return retryPolicy.delay(null, retryCount);
    });

    breaker.execute(future -> {
      counter.incrementAndGet();
      future.fail("FAILED");
    }).onComplete(ar -> {

    });

    await().untilAtomic(counter, is(6));
    await().untilAtomic(retryPolicyCounter, is(5));
  }
}
