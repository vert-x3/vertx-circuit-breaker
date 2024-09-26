package io.vertx.circuitbreaker.tests.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.circuitbreaker.impl.CircuitBreakerImpl;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Repeat;
import io.vertx.ext.unit.junit.RepeatRule;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(VertxUnitRunner.class)
public class CircuitBreakerMetricsTest {


  private Vertx vertx;
  private CircuitBreaker breaker;

  @Rule
  public RepeatRule rule = new RepeatRule();


  @Before
  public void setUp(TestContext tc) {
    vertx = Vertx.vertx();
    vertx.exceptionHandler(tc.exceptionHandler());
  }

  @After
  public void tearDown() {
    vertx.exceptionHandler(null);
    if (breaker != null) {
      breaker.close();
    }
    AtomicBoolean completed = new AtomicBoolean();
    vertx.close().onComplete(ar -> completed.set(ar.succeeded()));
    await().untilAtomic(completed, is(true));
  }


  @Test
  @Repeat(10)
  public void testWithSuccessfulCommands(TestContext tc) {
    breaker = CircuitBreaker.create("some-circuit-breaker", vertx, getOptions());
    Async async = tc.async();


    Future<Void> command1 = breaker.execute(commandThatWorks());
    Future<Void> command2 = breaker.execute(commandThatWorks());
    Future<Void> command3 = breaker.execute(commandThatWorks());

    Future.all(command1, command2, command3)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertEquals("some-circuit-breaker", metrics().getString("name"));
        assertEquals(CircuitBreakerState.CLOSED.name(), metrics().getString("state"));
        assertEquals(0, (int)metrics().getInteger("failures"));
        assertEquals(0, (int)metrics().getInteger("totalErrorCount"));
        assertEquals(3, (int)metrics().getInteger("totalSuccessCount"));
        assertEquals(0, (int)metrics().getInteger("totalTimeoutCount"));
        assertEquals(0, (int)metrics().getInteger("totalExceptionCount"));
        assertEquals(0, (int)metrics().getInteger("totalFailureCount"));
        assertEquals(100, (int)metrics().getInteger("totalSuccessPercentage"));
        assertEquals(0, (int)metrics().getInteger("totalErrorPercentage"));
        async.complete();
      });
  }

  private CircuitBreakerOptions getOptions() {
    return new CircuitBreakerOptions()
      .setNotificationAddress(CircuitBreakerOptions.DEFAULT_NOTIFICATION_ADDRESS);
  }

  @Test
  @Repeat(10)
  public void testWithFailedCommands(TestContext tc) {
    breaker = CircuitBreaker.create("some-circuit-breaker", vertx, getOptions());
    Async async = tc.async();

    Future<Void> command1 = breaker.execute(commandThatFails());
    Future<Void> command2 = breaker.execute(commandThatWorks());
    Future<Void> command3 = breaker.execute(commandThatWorks());
    Future<Void> command4 = breaker.execute(commandThatFails());

    Future.join(command1, command2, command3, command4)
      .onComplete(ar -> {
        assertEquals("some-circuit-breaker", metrics().getString("name"));
        assertEquals(CircuitBreakerState.CLOSED.name(), metrics().getString("state"));
        assertEquals(2, (int)metrics().getInteger("totalErrorCount"));
        assertEquals(2, (int)metrics().getInteger("totalSuccessCount"));
        assertEquals(0, (int)metrics().getInteger("totalTimeoutCount"));
        assertEquals(0, (int)metrics().getInteger("totalExceptionCount"));
        assertEquals(2, (int)metrics().getInteger("totalFailureCount"));
        assertEquals(4, (int)metrics().getInteger("totalOperationCount"));
        assertEquals(50, (int)metrics().getInteger("totalSuccessPercentage"));
        assertEquals(50, (int)metrics().getInteger("totalErrorPercentage"));
        async.complete();
      });
  }

  @Test
  @Repeat(10)
  public void testWithCrashingCommands(TestContext tc) {
    breaker = CircuitBreaker.create("some-circuit-breaker", vertx, getOptions());
    Async async = tc.async();

    Future<Void> command1 = breaker.execute(commandThatFails());
    Future<Void> command2 = breaker.execute(commandThatWorks());
    Future<Void> command3 = breaker.execute(commandThatWorks());
    Future<Void> command4 = breaker.execute(commandThatFails());
    Future<Void> command5 = breaker.execute(commandThatCrashes());

    Future.join(command1, command2, command3, command4, command5)
      .onComplete(ar -> {
        assertEquals("some-circuit-breaker", metrics().getString("name"));
        assertEquals(CircuitBreakerState.CLOSED.name(), metrics().getString("state"));
        assertEquals(3, (int)metrics().getInteger("totalErrorCount"));
        assertEquals(2, (int)metrics().getInteger("totalSuccessCount"));
        assertEquals(0, (int)metrics().getInteger("totalTimeoutCount"));
        assertEquals(1, (int)metrics().getInteger("totalExceptionCount"));
        assertEquals(2, (int)metrics().getInteger("totalFailureCount"));
        assertEquals(5, (int)metrics().getInteger("totalOperationCount"));
        assertEquals((2.0 / 5 * 100), (float)metrics().getFloat("totalSuccessPercentage"), 0.1);
        assertEquals((3.0 / 5 * 100), (float)metrics().getFloat("totalErrorPercentage"), 0.1);
        async.complete();
      });
  }

  @Test
  @Repeat(10)
  public void testWithTimeoutCommands(TestContext tc) {
    breaker = CircuitBreaker.create("some-circuit-breaker", vertx, getOptions().setTimeout(100));
    Async async = tc.async();

    Future<Void> command1 = breaker.execute(commandThatFails());
    Future<Void> command2 = breaker.execute(commandThatWorks());
    Future<Void> command3 = breaker.execute(commandThatWorks());
    Future<Void> command4 = breaker.execute(commandThatFails());
    Future<Void> command5 = breaker.execute(commandThatTimeout(100));

    Future.join(command1, command2, command3, command4, command5)
      .onComplete(ar -> {
        assertEquals("some-circuit-breaker", metrics().getString("name"));
        assertEquals(CircuitBreakerState.CLOSED.name(), metrics().getString("state"));
        assertEquals(3, (int)metrics().getInteger("totalErrorCount"));
        assertEquals(2, (int)metrics().getInteger("totalSuccessCount"));
        assertEquals(1, (int)metrics().getInteger("totalTimeoutCount"));
        assertEquals(0, (int)metrics().getInteger("totalExceptionCount"));
        assertEquals(2, (int)metrics().getInteger("totalFailureCount"));
        assertEquals(5, (int)metrics().getInteger("totalOperationCount"));
        assertEquals((2.0 / 5 * 100), (float)metrics().getFloat("totalSuccessPercentage"), 0.1);
        assertEquals((3.0 / 5 * 100), (float)metrics().getFloat("totalErrorPercentage"), 0.1);
        async.complete();
      });
  }


  @Test
  @Repeat(10)
  public void testLatencyComputation(TestContext tc) {
    breaker = CircuitBreaker.create("some-circuit-breaker", vertx, getOptions());
    Async async = tc.async();


    int count = 1000;

    IntStream.range(0, count)
      .mapToObj(i -> breaker.execute(commandThatWorks()))
      .collect(collectingAndThen(toList(), Future::all))
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertEquals("some-circuit-breaker", metrics().getString("name"));
        assertEquals(CircuitBreakerState.CLOSED.name(), metrics().getString("state"));
        assertEquals(0, (int)metrics().getInteger("failures"));
        assertEquals(0, (int)metrics().getInteger("totalErrorCount"));
        assertEquals(count, (int)metrics().getInteger("totalSuccessCount"));
        assertEquals(0, (int)metrics().getInteger("totalTimeoutCount"));
        assertEquals(0, (int)metrics().getInteger("totalExceptionCount"));
        assertEquals(0, (int)metrics().getInteger("totalFailureCount"));
        assertEquals(count, (int)metrics().getInteger("totalOperationCount"));
        assertEquals(100, metrics().getFloat("totalSuccessPercentage"), 0.1);
        assertEquals(0, metrics().getFloat("totalErrorPercentage"), 0.1);
        async.complete();
      });
  }

  @Test
  @Repeat(100)
  public void testEviction(TestContext tc) {
    breaker = CircuitBreaker.create("some-circuit-breaker", vertx, getOptions().setMetricsRollingWindow(10));
    Async async = tc.async();


    int count = 1000;

    List<Future<Void>> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      list.add(breaker.execute(commandThatWorks()));
    }

    Future.all(list)
      .onComplete(ar -> {
        assertTrue(ar.succeeded());
        assertEquals(1000, (int)metrics().getInteger("totalOperationCount"));
        assertTrue(metrics().getInteger("rollingOperationCount") <= 1000);
        async.complete();
      });
  }


  private Handler<Promise<Void>> commandThatWorks() {
    return (future -> vertx.setTimer(5, l -> future.complete(null)));
  }

  private Handler<Promise<Void>> commandThatFails() {
    return (future -> vertx.setTimer(5, l -> future.fail("expected failure")));
  }

  private Handler<Promise<Void>> commandThatCrashes() {
    return (future -> {
      throw new RuntimeException("Expected error");
    });
  }

  private Handler<Promise<Void>> commandThatTimeout(int timeout) {
    return (future -> vertx.setTimer(timeout + 500, l -> future.complete(null)));
  }

  private JsonObject metrics() {
    return ((CircuitBreakerImpl) breaker).getMetrics();
  }

}
