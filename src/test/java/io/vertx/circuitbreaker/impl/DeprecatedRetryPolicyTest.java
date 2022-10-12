package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.jayway.awaitility.Awaitility.*;
import static org.hamcrest.Matchers.*;

/**
 * Checks that retry policy is being applied
 */
public class DeprecatedRetryPolicyTest {
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
    runRetryPolicyTest(retry -> retry * 100L);
  }

  @Test
  public void testWithZeroRetryPolicy() {
    runRetryPolicyTest(retry -> 0L);
  }

  @Test
  public void testWithNegativeRetryPolicy() {
    runRetryPolicyTest(retry -> -1L);
  }

  /**
   * Helper method to run retry policy tests
   */
  private void runRetryPolicyTest(Function<Integer, Long> retryPolicy) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
      new CircuitBreakerOptions().setMaxFailures(5).setMaxRetries(5));
    AtomicInteger counter = new AtomicInteger();
    AtomicInteger retryPolicyCounter = new AtomicInteger();

    breaker.retryPolicy(retry -> {
      retryPolicyCounter.incrementAndGet();
      return retryPolicy.apply(retry);
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
