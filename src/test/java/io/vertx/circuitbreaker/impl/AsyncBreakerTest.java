package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reproducer for https://github.com/vert-x3/issues/issues/294 (copied to
 * https://github.com/vert-x3/vertx-circuit-breaker/issues/14).
 */
@RunWith(VertxUnitRunner.class)
public class AsyncBreakerTest {

  private Vertx vertx;
  private CircuitBreaker breaker;
  private int count;

  private static Logger LOG = LoggerFactory.getLogger(AsyncBreakerTest.class);

  @Before
  public void setUp() {
    vertx = Vertx.vertx();

    breaker = CircuitBreaker.create("collector-circuit-breaker", vertx,
      new CircuitBreakerOptions()
        .setMaxFailures(2)
        .setTimeout(1_000)
        .setFallbackOnFailure(false)
        .setResetTimeout(2_000)
        .setNotificationPeriod(0));

    count = 0;
  }

  @After
  public void tearDown(TestContext tc) {
    vertx.close(tc.asyncAssertSuccess());
  }

  private void x(TestContext tc, int id) {
    Async async = tc.async(10);
    breaker.executeWithFallback(future -> {
      vertx.setTimer(100 + (id * 10), handler -> {
        synchronized (this) {
          count++;
          if (count < 5 || count > 12) {
            future.complete("OK");
            async.complete();
          } else {
            future.fail("kapot");
            async.complete();
          }
        }
      });

    }, fallback -> {
      LOG.info("OPEN {}", id);
      async.complete();
      return "OPEN";
    });
  }

  @Test
  public void test1(TestContext tc) throws InterruptedException {

    for (int i = 0; i < 20; ++i) {
      x(tc, i);
    }

    breaker.openHandler(h -> LOG.info("Breaker open"));
    breaker.closeHandler(h -> tc.fail("should not close"));
    breaker.halfOpenHandler(h -> LOG.info("Breaker half open"));
  }

  @Test
  public void test2(TestContext tc) throws InterruptedException {
    Async async = tc.async();

    for (int i = 0; i < 20; ++i) {
      x(tc, i);
    }

    breaker.openHandler(h -> LOG.info("Breaker open"));
    breaker.closeHandler(h -> {
      LOG.info("Breaker closed");
      async.complete();
    });
    breaker.halfOpenHandler(h -> LOG.info("Breaker half open"));

    LOG.info("Waiting for test to complete");

    Thread.sleep(3000);
    LOG.info("Sleep done");
    for (int i = 0; i < 5; ++i) {
      x(tc, i);
    }
  }


}
