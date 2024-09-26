/*
 * Copyright (c) 2011-2016 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.circuitbreaker.tests.impl;

import io.vertx.circuitbreaker.*;
import io.vertx.circuitbreaker.impl.CircuitBreakerImpl;
import io.vertx.core.*;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Test the basic behavior of the circuit breaker.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(VertxUnitRunner.class)
public class CircuitBreakerImplTest {
  private Vertx vertx;
  private CircuitBreaker breaker;

  @Rule
  public RepeatRule rule = new RepeatRule();

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
  }

  @After
  public void tearDown() {
    if (breaker != null) {
      breaker.close();
    }
    AtomicBoolean completed = new AtomicBoolean();
    vertx.close().onComplete(ar -> completed.set(ar.succeeded()));
    await().untilAtomic(completed, is(true));
  }

  @Test
  public void testCreationWithDefault() {
    breaker = CircuitBreaker.create("name", vertx);
    assertEquals("name", breaker.name());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());
  }

  @Test
  @Repeat(5)
  public void testOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicBoolean operationCalled = new AtomicBoolean();
    AtomicReference<String> completionCalled = new AtomicReference<>();
    breaker.<String>execute(fut -> {
      operationCalled.set(true);
      fut.complete("hello");
    }).onComplete(ar -> completionCalled.set(ar.result()));

    await().until(operationCalled::get);
    await().until(() -> completionCalled.get().equalsIgnoreCase("hello"));
  }

  @Test
  @Repeat(5)
  public void testWithCustomPredicateOk() {
    breaker = CircuitBreaker.create("test", vertx).failurePolicy(ar -> {
      return ar.failed() && ar.cause().getStackTrace().length > 0;
    });

    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicBoolean operationCalled = new AtomicBoolean();
    AtomicReference<String> completionCalled = new AtomicReference<>();
    breaker.<String>execute(fut -> {
      operationCalled.set(true);
      fut.fail("some fake exception");
    }).onComplete(ar -> {
      completionCalled.set(ar.cause().getMessage());
      assertTrue(ar.failed());
    });

    await().until(operationCalled::get);
    await().until(() -> completionCalled.get().equalsIgnoreCase("some fake exception"));
  }

  @Test
  @Repeat(5)
  public void testWithUserFutureOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicBoolean operationCalled = new AtomicBoolean();
    AtomicReference<String> completionCalled = new AtomicReference<>();

    Promise<String> userFuture = Promise.promise();
    userFuture.future().onComplete(ar ->
      completionCalled.set(ar.result()));

    breaker.executeAndReport(userFuture, fut -> {
      operationCalled.set(true);
      fut.complete("hello");
    });

    await().until(operationCalled::get);
    await().until(() ->  completionCalled.get().equalsIgnoreCase("hello"));
  }

  @Test
  @Repeat(5)
  public void testWithUserFutureWithCustomPredicateOk() {
    breaker = CircuitBreaker.create("test", vertx).failurePolicy(ar -> {
      return ar.failed() && ar.cause().getStackTrace().length > 0;
    });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicBoolean operationCalled = new AtomicBoolean();
    AtomicReference<String> completionCalled = new AtomicReference<>();

    Promise<String> userFuture = Promise.promise();
    userFuture.future().onComplete(ar -> {
      completionCalled.set(ar.cause().getMessage());
      assertTrue(ar.failed());
    });

    breaker.executeAndReport(userFuture, fut -> {
      operationCalled.set(true);
      fut.fail("some custom exception");
    });

    await().until(operationCalled::get);
    await().until(() -> completionCalled.get().equalsIgnoreCase("some custom exception"));
  }

  @Test
  public void testAsynchronousOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicBoolean called = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();
    breaker.<String>execute(future ->
      vertx.setTimer(100, l -> {
        called.set(true);
        future.complete("hello");
      })
    ).onComplete(ar -> result.set(ar.result()));

    await().until(called::get);
    await().untilAtomic(result, is("hello"));
  }

  @Test
  public void testAsynchronousWithCustomPredicateOk() {
    breaker = CircuitBreaker.create("test", vertx).failurePolicy(ar -> {
      return ar.failed() && ar.cause().getStackTrace().length > 0;
    });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicBoolean called = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();
    breaker.<String>execute(future ->
      vertx.setTimer(100, l -> {
        called.set(true);
        future.fail("some custom exception");
      })
    ).onComplete(ar -> {
      result.set(ar.cause().getMessage());
      assertTrue(ar.failed());
    });
    ;

    await().until(called::get);
    await().untilAtomic(result, is("some custom exception"));
  }

  @Test
  public void testAsynchronousWithUserFutureOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicBoolean called = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();

    Promise<String> userFuture = Promise.promise();
    userFuture.future().onComplete(ar -> result.set(ar.result()));

    breaker.executeAndReport(userFuture, future ->
      vertx.setTimer(100, l -> {
        called.set(true);
        future.complete("hello");
      })
    );

    await().until(called::get);
    await().untilAtomic(result, is("hello"));
  }

  @Test
  public void testAsynchronousWithUserFutureAndWithCustomPredicateOk() {
    breaker = CircuitBreaker.create("test", vertx).failurePolicy(ar -> {
      return ar.failed() && ar.cause() instanceof ClassNotFoundException;
    });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicBoolean called = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();

    Promise<String> userFuture = Promise.promise();
    userFuture.future().onComplete(ar -> {
      result.set(ar.cause().getMessage());
      assertTrue(ar.failed());
    });
    ;

    breaker.executeAndReport(userFuture, future ->
      vertx.setTimer(100, l -> {
        called.set(true);
        future.fail(new NullPointerException("some custom exception"));
      })
    );

    await().until(called::get);
    await().untilAtomic(result, is("some custom exception"));
  }

  @Test
  public void testRollingWindowFailuresAreDecreased() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions()
    														.setMaxFailures(10)
    														.setFailuresRollingWindow(10000));
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    IntStream.range(0,  9).forEach(i -> breaker.execute(v -> v.fail(new RuntimeException("oh no, but this is expected"))));
    await().until(() -> breaker.failureCount() == 9);
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    await().atMost(11, TimeUnit.SECONDS).until(() -> breaker.failureCount() < 9);

    assertTrue(breaker.failureCount() < 9);
  }

  @Test
  @Repeat(5)
  public void testOpenAndCloseHandler() {
    AtomicInteger spyOpen = new AtomicInteger();
    AtomicInteger spyClosed = new AtomicInteger();

    AtomicReference<Throwable> lastException = new AtomicReference<>();

    breaker = CircuitBreaker.create("name", vertx, new CircuitBreakerOptions().setResetTimeout(-1))
      .openHandler((v) -> spyOpen.incrementAndGet())
      .closeHandler((v) -> spyClosed.incrementAndGet());

    assertEquals(0, spyOpen.get());
    assertEquals(0, spyClosed.get());

    // First failure
    breaker.execute(v -> {
      throw new RuntimeException("oh no, but this is expected");
    })
      .onComplete(ar -> lastException.set(ar.cause()));

    assertEquals(0, spyOpen.get());
    assertEquals(0, spyClosed.get());
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    assertNotNull(lastException.get());
    lastException.set(null);

    for (int i = 1; i < CircuitBreakerOptions.DEFAULT_MAX_FAILURES; i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      })
        .onComplete(ar -> lastException.set(ar.cause()));
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertEquals(1, spyOpen.get());
    assertNotNull(lastException.get());

    ((CircuitBreakerImpl) breaker).reset(true);
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());
    assertEquals(1, spyOpen.get());
    assertEquals(1, spyClosed.get());
  }

  @Test
  @Repeat(5)
  public void testHalfOpen() {
    AtomicBoolean thrown = new AtomicBoolean(false);
    Context ctx = vertx.getOrCreateContext()
      .exceptionHandler(ex -> // intercept exceptions
        thrown.set(true));

    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions()
      .setResetTimeout(200)
      .setMaxFailures(1));

    Handler<Promise<Void>> fail = p -> p.fail("fail");
    Handler<Promise<Void>> success = Promise::complete;

    ctx.runOnContext(v -> {
      breaker.execute(fail);
      breaker.execute(fail);
    });

    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);

    ctx.runOnContext(v -> {
      breaker.execute(fail);
    });

    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);

    ctx.runOnContext(v -> {
      breaker.execute(success);
    });

    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);

    assertFalse(thrown.get());
  }

  @Test
  @Repeat(5)
  public void testExceptionOnSynchronousCode() {
    AtomicBoolean called = new AtomicBoolean(false);
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setFallbackOnFailure(false)
      .setResetTimeout(-1);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(t -> {
        called.set(true);
        return "fallback";
      });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN  ||
      breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertFalse(called.get());

    AtomicBoolean spy = new AtomicBoolean();
    breaker.execute(v -> spy.set(true));
    assertFalse(spy.get());
    assertTrue(called.get());
  }

  @Test
  @Repeat(5)
  public void testExceptionOnSynchronousCodeWithExecute() {
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setFallbackOnFailure(false)
      .setResetTimeout(-1);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(t -> "fallback");
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      Promise<String> future = Promise.promise();
      AtomicReference<String> result = new AtomicReference<>();
      breaker.executeAndReport(future, v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
      future.future().onComplete(ar -> result.set(ar.result()));
      assertNull(result.get());
    }

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    assertEquals(CircuitBreakerState.OPEN, breaker.state());

    AtomicBoolean spy = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();
    Promise<String> fut = Promise.promise();
    fut.future().onComplete(ar ->
      result.set(ar.result())
    );
    breaker.executeAndReport(fut, v -> spy.set(true));
    assertFalse(spy.get());
    assertEquals("fallback", result.get());
  }

  @Test
  public void testFailureOnAsynchronousCode() {
    AtomicBoolean called = new AtomicBoolean(false);
    AtomicReference<String> result = new AtomicReference<>();
    CircuitBreakerOptions options = new CircuitBreakerOptions().setResetTimeout(-1);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(
        future -> vertx.setTimer(100, l -> future.fail("expected failure"))
      ).onComplete(ar -> result.set(ar.result()));
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    assertFalse(called.get());

    AtomicBoolean spy = new AtomicBoolean();
    breaker.<String>execute(
      future -> vertx.setTimer(100, l -> {
        future.fail("expected failure");
        spy.set(true);
      }))
      .onComplete(ar -> result.set(ar.result()));
    await().untilAtomic(called, is(true));
    assertFalse(spy.get());
    assertEquals("fallback", result.get());
  }

  @Test
  public void testFailureOnAsynchronousCodeWithCustomPredicate() {
    AtomicBoolean called = new AtomicBoolean(false);
    AtomicReference<String> result = new AtomicReference<>();
    CircuitBreakerOptions options = new CircuitBreakerOptions().setResetTimeout(-1);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      })
      .failurePolicy(ar -> {
        return ar.failed() && ar.cause().getStackTrace().length == 0;
      });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(
        future -> vertx.setTimer(100, l -> future.fail("expected failure"))
      ).onComplete(ar -> result.set(ar.result()));
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    assertFalse(called.get());

    AtomicBoolean spy = new AtomicBoolean();
    breaker.<String>execute(
        future -> vertx.setTimer(100, l -> {
          future.fail("expected failure");
          spy.set(true);
        }))
      .onComplete(ar -> {
        result.set(ar.result());
      });
    ;
    await().untilAtomic(called, is(true));
    assertFalse(spy.get());
    assertEquals("fallback", result.get());
  }

  @Test
  @Repeat(5)
  public void testResetAttempt() {
    AtomicBoolean called = new AtomicBoolean(false);
    CircuitBreakerOptions options = new CircuitBreakerOptions().setResetTimeout(100);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN  || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertFalse(called.get());

    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);

    AtomicBoolean spy = new AtomicBoolean();
    breaker.execute(v -> {
      spy.set(true);
      v.complete();
    });
    assertTrue(spy.get());
    assertFalse(called.get());
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());
  }

  @Test
  @Repeat(5)
  public void testResetAttemptThatFails() {
    AtomicBoolean called = new AtomicBoolean(false);
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(100)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      });
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertTrue(called.get());

    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);
    called.set(false);

    AtomicReference<String> result = new AtomicReference<>();
    breaker.<String>execute(v -> {
      throw new RuntimeException("oh no, but this is expected");
    }).onComplete(ar -> result.set(ar.result()));

    await().until(called::get);
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertEquals("fallback", result.get());
  }

  @Test
  public void testTimeout() {
    AtomicBoolean called = new AtomicBoolean(false);
    CircuitBreakerOptions options = new CircuitBreakerOptions().setTimeout(100);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicInteger failureCount = new AtomicInteger();
    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(v -> {
        try {
          Thread.sleep(500);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        v.complete("done");
      }).onComplete(ar -> {
        if (ar.failed()) failureCount.incrementAndGet();
      });
    }

    assertEquals(CircuitBreakerState.OPEN, breaker.state());
    assertFalse(called.get());
    assertEquals(options.getMaxFailures(), failureCount.get());

    AtomicBoolean spy = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();
    breaker.<String>execute(v -> {
      spy.set(true);
      v.complete();
    })
      .onComplete(ar -> result.set(ar.result()));
    assertFalse(spy.get());
    assertTrue(called.get());
    assertEquals("fallback", result.get());
  }

  @Test
  public void testTimeoutWithFallbackCalled() {
    AtomicBoolean called = new AtomicBoolean(false);
    CircuitBreakerOptions options = new CircuitBreakerOptions().setTimeout(100)
      .setResetTimeout(5000)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    AtomicInteger count = new AtomicInteger();
    for (int i = 0; i < options.getMaxFailures() + 3; i++) {
      breaker.execute(v -> {
        try {
          Thread.sleep(500);
          v.complete("done");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          v.fail(e);
        }
      }).onComplete(ar -> {
        if (ar.result().equals("fallback")) {
          count.incrementAndGet();
        }
      });
    }

    assertEquals(CircuitBreakerState.OPEN, breaker.state());
    assertTrue(called.get());
    assertEquals(options.getMaxFailures() + 3, count.get());
  }

  @Test
  public void testResetAttemptOnTimeout() {
    AtomicBoolean called = new AtomicBoolean(false);
    AtomicBoolean hasBeenOpened = new AtomicBoolean(false);
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(100)
      .setTimeout(10)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      })
      .openHandler(v -> hasBeenOpened.set(true));
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(future -> {
        // Do nothing with the future, this is a very bad thing.
      });
    }
    await().untilAtomic(hasBeenOpened, is(true));
    assertTrue(called.get());

    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);
    called.set(false);

    breaker.execute(Promise::complete);
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    await().untilAtomic(called, is(false));
  }

  @Test
  @Repeat(10)
  public void testResetAttemptThatFailsOnTimeout() {
    AtomicBoolean called = new AtomicBoolean(false);
    AtomicBoolean hasBeenOpened = new AtomicBoolean(false);
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(100)
      .setTimeout(10)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      })
      .openHandler(v -> hasBeenOpened.set(true));
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(future -> {
        // Do nothing with the future, this is a very bad thing.
      });
    }
    await().untilAtomic(hasBeenOpened, is(true));
    assertTrue(called.get());
    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);
    hasBeenOpened.set(false);
    called.set(false);

    breaker.execute(future -> {
      // Do nothing with the future, this is a very bad thing.
    });
    // Failed again, open circuit
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    await().untilAtomic(called, is(true));
    await().untilAtomic(hasBeenOpened, is(true));

    hasBeenOpened.set(false);
    called.set(false);

    breaker.execute(future -> {
      // Do nothing with the future, this is a very bad thing.
    });
    // Failed again, open circuit
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    await().untilAtomic(called, is(true));
    await().untilAtomic(hasBeenOpened, is(true));

    hasBeenOpened.set(false);
    called.set(false);

    hasBeenOpened.set(false);
    called.set(false);

    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED  || breaker.state() == CircuitBreakerState.HALF_OPEN);

    // If HO - need to get next request executed and wait until we are closed
    breaker.execute(Promise::complete);
    await().until(() -> {
      if (breaker.state() == CircuitBreakerState.CLOSED) {
        return true;
      } else {
        breaker.execute(Promise::complete);
        return false;
      }
    });
    called.set(false);
    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(f -> f.complete(null));
    }

    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    await().untilAtomic(hasBeenOpened, is(false));
  }

  @Test
  public void testThatOnlyOneRequestIsCheckedInHalfOpen() {
    AtomicBoolean called = new AtomicBoolean(false);
    AtomicBoolean hasBeenOpened = new AtomicBoolean(false);
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(1000)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      })
      .openHandler(v -> hasBeenOpened.set(true));
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(future -> future.fail("expected failure"));
    }
    await().untilAtomic(hasBeenOpened, is(true));
    assertTrue(called.get());

    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);
    called.set(false);

    AtomicInteger fallbackCalled = new AtomicInteger();
    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.executeWithFallback(
        future -> vertx.setTimer(500, l -> future.complete()),
        v -> {
          fallbackCalled.incrementAndGet();
          return "fallback";
        });
    }

    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    assertEquals(options.getMaxFailures() - 1, fallbackCalled.get());
  }

  @Test
  public void testFailureWhenThereIsNoFallback() {
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(50000)
      .setTimeout(300)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options);

    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    List<AsyncResult<String>> results = new ArrayList<>();
    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(future -> future.fail("expected failure"))
        .onComplete(ar -> results.add(ar));
    }
    await().until(() -> results.size() == options.getMaxFailures());
    results.forEach(ar -> {
      assertTrue(ar.failed());
      assertNotNull(ar.cause());
      assertEquals("expected failure", ar.cause().getMessage());
    });

    results.clear();

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    breaker.<String>execute(future -> future.fail("expected failure"))
      .onComplete(ar -> results.add(ar));
    await().until(() -> results.size() == 1);
    results.forEach(ar -> {
      assertTrue(ar.failed());
      assertNotNull(ar.cause());
      assertTrue(ar.cause() instanceof OpenCircuitException);
      assertEquals("open circuit", ar.cause().getMessage());
    });

    ((CircuitBreakerImpl) breaker).reset(true);

    assertEquals(CircuitBreakerState.CLOSED, breaker.state());
    results.clear();

    breaker.<String>execute(future -> {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        // Ignored.
      }
    })
      .onComplete(ar -> results.add(ar));
    await().until(() -> results.size() == 1);
    results.forEach(ar -> {
      assertTrue(ar.failed());
      assertNotNull(ar.cause());
      assertTrue(ar.cause() instanceof TimeoutException);
      assertEquals("operation timeout", ar.cause().getMessage());
    });
  }

  @Test
  public void testWhenFallbackThrowsAnException() {
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(5000)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options);

    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    List<AsyncResult<String>> results = new ArrayList<>();
    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>executeWithFallback(
        future -> future.fail("expected failure"),
        t -> {
          throw new RuntimeException("boom");
        })
        .onComplete(ar -> results.add(ar));
    }
    await().until(() -> results.size() == options.getMaxFailures());
    results.forEach(ar -> {
      assertTrue(ar.failed());
      assertNotNull(ar.cause());
      assertEquals("boom", ar.cause().getMessage());
    });

    results.clear();

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    breaker.<String>executeWithFallback(
      future -> future.fail("expected failure"),
      t -> {
        throw new RuntimeException("boom");
      })
      .onComplete(ar -> results.add(ar));
    await().until(() -> results.size() == 1);
    results.forEach(ar -> {
      assertTrue(ar.failed());
      assertNotNull(ar.cause());
      assertEquals("boom", ar.cause().getMessage());
    });
  }


  @Test
  public void testTheExceptionReceivedByFallback() {
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(50000)
      .setTimeout(300)
      .setFallbackOnFailure(true);
    List<Throwable> failures = new ArrayList<>();

    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(failures::add);

    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(future -> future.fail("expected failure"));
    }
    await().until(() -> failures.size() == options.getMaxFailures());
    failures.forEach(ar -> {
      assertNotNull(ar);
      assertEquals("expected failure", ar.getMessage());
    });

    failures.clear();

    ((CircuitBreakerImpl) breaker).reset(true);

    assertEquals(CircuitBreakerState.CLOSED, breaker.state());
    failures.clear();

    breaker.<String>execute(future -> {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        // Ignored.
      }
    });
    await().until(() -> failures.size() == 1);
    failures.forEach(ar -> {
      assertNotNull(ar);
      assertTrue(ar instanceof TimeoutException);
      assertEquals("operation timeout", ar.getMessage());
    });

    ((CircuitBreakerImpl) breaker).reset(true);

    assertEquals(CircuitBreakerState.CLOSED, breaker.state());
    failures.clear();

    breaker.<String>execute(future -> {
      throw new RuntimeException("boom");
    });
    await().until(() -> failures.size() == 1);
    failures.forEach(ar -> {
      assertNotNull(ar);
      assertEquals("boom", ar.getMessage());
    });
  }

  @Test
  @Repeat(5)
  public void testRetries() {
    CircuitBreakerOptions options = new CircuitBreakerOptions().setMaxRetries(5).setMaxFailures(4).setTimeout(100)
      .setFallbackOnFailure(true);
    List<Throwable> failures = new ArrayList<>();

    AtomicInteger calls = new AtomicInteger();
    breaker = CircuitBreaker.create("test", vertx, options);


    final AtomicReference<Future> result = new AtomicReference<>();
    vertx.runOnContext(v -> {
      result.set(breaker.execute(future -> {
        calls.incrementAndGet();
        future.fail("boom");
      }));
    });

    await().untilAtomic(calls, is(6));
    assertTrue(result.get().failed());
    assertEquals(1, breaker.failureCount());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    ((CircuitBreakerImpl) breaker).reset(true);
    calls.set(0);
    result.set(null);

    vertx.runOnContext(v -> {
      result.set(breaker.execute(future -> {
        if (calls.incrementAndGet() >= 4) {
          future.complete();
        } else {
          future.fail("boom");
        }
      }));
    });


    await().untilAtomic(calls, is(4));
    assertTrue(result.get().succeeded());
    assertEquals(0, breaker.failureCount());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    ((CircuitBreakerImpl) breaker).reset(true);
    calls.set(0);

    vertx.runOnContext(v -> {
      for (int i = 0; i < options.getMaxFailures() + 1; i++) {
        breaker.execute(future -> {
          try {
            calls.incrementAndGet();
            Thread.sleep(150);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
      }
    });

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);

    calls.set(0);
    ((CircuitBreakerImpl) breaker).reset(true);
    AtomicReference<Future> result2 = new AtomicReference<>();
    vertx.runOnContext(v -> {
        result2.set(breaker.execute(future -> {
        if (calls.incrementAndGet() == 4) {
          future.complete();
        } else {
          try {
            Thread.sleep(150);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }));
      for (int i = 0; i < options.getMaxFailures(); i++) {
        breaker.execute(future -> {
          future.fail("boom");
        });
      }
    });


    await().until(() -> result2.get() != null  && result2.get().failed());
    assertEquals(options.getMaxFailures() + 1, breaker.failureCount());
    assertEquals(CircuitBreakerState.OPEN, breaker.state());


    ((CircuitBreakerImpl) breaker).reset(true);
    breaker.fallback(failures::add);
    calls.set(0);
    result.set(null);

    vertx.runOnContext(v -> {
      result.set(breaker.execute(future -> {
        try {
          Thread.sleep(150);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }));
    });


    await().until(() -> failures.size() == 1);
    failures.forEach(ar -> {
      assertNotNull(ar);
      assertTrue(ar instanceof TimeoutException);
      assertEquals("operation timeout", ar.getMessage());
    });
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    ((CircuitBreakerImpl) breaker).reset(true);
    calls.set(0);
    result.set(null);


    vertx.runOnContext(v -> {
      result.set(breaker.execute(future -> {
        if (calls.incrementAndGet() == 4) {
          future.complete();
        } else {
          try {
            Thread.sleep(150);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }));
    });


    await().untilAtomic(calls, is(4));
    assertTrue(result.get().succeeded());
    assertEquals(0, breaker.failureCount());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidBucketSize() {
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setNotificationAddress(CircuitBreakerOptions.DEFAULT_NOTIFICATION_ADDRESS)
      .setMetricsRollingBuckets(7);
    CircuitBreaker.create("test", vertx, options);
  }

  @Test
  public void operationTimersShouldBeRemovedToAvoidOOM(TestContext ctx) {
    breaker = CircuitBreaker.create("cb", vertx, new CircuitBreakerOptions().setTimeout(600_000));
    Async async = ctx.async(3000);
    long id = vertx.setPeriodic(1, l -> {
      breaker.execute(prom -> prom.complete(new byte[10 * 1024 * 1024])).onSuccess(v -> async.countDown()).onFailure(ctx::fail);
    });
    async.await();
    // Test will throw OOM if operation timers are not removed
  }
}
