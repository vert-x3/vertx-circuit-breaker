package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

/**
 * Checks the number of retries.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class NumberOfRetryTest {

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
  public void testWithoutRetry() {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
      new CircuitBreakerOptions().setMaxFailures(5));
    AtomicInteger counter = new AtomicInteger();

    breaker.execute(future -> {
      counter.incrementAndGet();
      future.fail("FAILED");
    }).onComplete(ar -> {

    });

    await().untilAtomic(counter, is(1));
  }

  @Test
  public void testWithRetrySetToZero() {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
      new CircuitBreakerOptions().setMaxFailures(5).setMaxRetries(0));
    AtomicInteger counter = new AtomicInteger();

    breaker.execute(future -> {
      counter.incrementAndGet();
      future.fail("FAILED");
    }).onComplete(ar -> {

    });

    await().untilAtomic(counter, is(1));
  }

  @Test
  public void testWithRetrySetToOne() {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
      new CircuitBreakerOptions().setMaxFailures(5).setMaxRetries(1));
    AtomicInteger counter = new AtomicInteger();

    breaker.execute(future -> {
      counter.incrementAndGet();
      future.fail("FAILED");
    }).onComplete(ar -> {

    });

    await().untilAtomic(counter, is(2));
  }

  @Test
  public void testWithRetrySetToFive() {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
      new CircuitBreakerOptions().setMaxFailures(5).setMaxRetries(5));
    AtomicInteger counter = new AtomicInteger();

    breaker.execute(future -> {
      counter.incrementAndGet();
      future.fail("FAILED");
    }).onComplete(ar -> {

    });

    await().untilAtomic(counter, is(6));
  }
}
