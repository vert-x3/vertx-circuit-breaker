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

package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.*;
import io.vertx.core.*;
import io.vertx.core.impl.NoStackTraceThrowable;
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

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.is;

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
    vertx.close(ar -> completed.set(ar.succeeded()));
    await().untilAtomic(completed, is(true));
  }

  @Test
  public void testCreationWithDefault() {
    breaker = CircuitBreaker.create("name", vertx);
    assertThat(breaker.name()).isEqualTo("name");
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
  }

  @Test
  @Repeat(5)
  public void testOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions().setAsyncFailurePolicy(ar -> {
      if(ar.failed() && !(ar.cause() instanceof NoStackTraceThrowable)) {
        return true;
      }

      return false;
    }));

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    AtomicBoolean operationCalled = new AtomicBoolean();
    AtomicReference<String> completionCalled = new AtomicReference<>();
    breaker.<String>execute(fut -> {
      operationCalled.set(true);
      fut.fail("some fake exception");
    }).onComplete(ar -> {
      completionCalled.set(ar.cause().getMessage());
      assertThat(ar.failed()).isTrue();
    });

    await().until(operationCalled::get);
    await().until(() -> completionCalled.get().equalsIgnoreCase("some fake exception"));
  }

  @Test
  @Repeat(5)
  public void testWithUserFutureOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions().setAsyncFailurePolicy(ar -> {
      if(ar.failed() && !(ar.cause() instanceof NoStackTraceThrowable)) {
        return true;
      }

      return false;
    }));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    AtomicBoolean operationCalled = new AtomicBoolean();
    AtomicReference<String> completionCalled = new AtomicReference<>();

    Promise<String> userFuture = Promise.promise();
    userFuture.future().onComplete(ar -> {
      completionCalled.set(ar.cause().getMessage());
      assertThat(ar.failed()).isTrue();
    });

    breaker.executeAndReport(userFuture, fut -> {
      operationCalled.set(true);
      fut.fail("some custom exception");
    });

    await().until(operationCalled::get);
    await().until(() ->  completionCalled.get().equalsIgnoreCase("some custom exception"));
  }

  @Test
  public void testAsynchronousOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions().setAsyncFailurePolicy(ar -> {
      if(ar.failed() && !(ar.cause() instanceof NoStackTraceThrowable)) {
        return true;
      }

      return false;
    }));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    AtomicBoolean called = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();
    breaker.<String>execute(future ->
      vertx.setTimer(100, l -> {
        called.set(true);
        future.fail("some custom exception");
      })
    ).onComplete(ar -> {
      result.set(ar.cause().getMessage());
      assertThat(ar.failed()).isTrue();
    });;

    await().until(called::get);
    await().untilAtomic(result, is("some custom exception"));
  }

  @Test
  public void testAsynchronousWithUserFutureOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions().setAsyncFailurePolicy(ar -> {
      if(ar.failed() && ar.cause() instanceof ClassNotFoundException) {
        return true;
      }

      return false;
    }));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    AtomicBoolean called = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();

    Promise<String> userFuture = Promise.promise();
    userFuture.future().onComplete(ar -> {
      result.set(ar.cause().getMessage());
      assertThat(ar.failed()).isTrue();
    });;

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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    IntStream.range(0,  9).forEach(i -> breaker.execute(v -> v.fail(new RuntimeException("oh no, but this is expected"))));
    await().until(() -> breaker.failureCount() == 9);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    await().atMost(11, TimeUnit.SECONDS).until(() -> breaker.failureCount() < 9);

    assertThat(breaker.failureCount()).isLessThan(9);
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

    assertThat(spyOpen.get()).isEqualTo(0);
    assertThat(spyClosed.get()).isEqualTo(0);

    // First failure
    breaker.execute(v -> {
      throw new RuntimeException("oh no, but this is expected");
    })
      .onComplete(ar -> lastException.set(ar.cause()));

    assertThat(spyOpen.get()).isEqualTo(0);
    assertThat(spyClosed.get()).isEqualTo(0);
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    assertThat(lastException.get()).isNotNull();
    lastException.set(null);

    for (int i = 1; i < CircuitBreakerOptions.DEFAULT_MAX_FAILURES; i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      })
        .onComplete(ar -> lastException.set(ar.cause()));
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertThat(spyOpen.get()).isEqualTo(1);
    assertThat(lastException.get()).isNotNull();

    ((CircuitBreakerImpl) breaker).reset(true);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    assertThat(spyOpen.get()).isEqualTo(1);
    assertThat(spyClosed.get()).isEqualTo(1);
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
    Handler<Promise<Void>> success = p -> p.complete();

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

    assertThat(thrown.get()).isFalse();
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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN  ||
      breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertThat(called.get()).isEqualTo(false);

    AtomicBoolean spy = new AtomicBoolean();
    breaker.execute(v -> spy.set(true));
    assertThat(spy.get()).isEqualTo(false);
    assertThat(called.get()).isEqualTo(true);
  }

  @Test
  @Repeat(5)
  public void testExceptionOnSynchronousCodeWithExecute() {
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setFallbackOnFailure(false)
      .setResetTimeout(-1);
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(t -> "fallback");
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      Promise<String> future = Promise.promise();
      AtomicReference<String> result = new AtomicReference<>();
      breaker.executeAndReport(future, v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
      future.future().onComplete(ar -> result.set(ar.result()));
      assertThat(result.get()).isNull();
    }

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);

    AtomicBoolean spy = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();
    Promise<String> fut = Promise.promise();
    fut.future().onComplete(ar ->
      result.set(ar.result())
    );
    breaker.executeAndReport(fut, v -> spy.set(true));
    assertThat(spy.get()).isEqualTo(false);
    assertThat(result.get()).isEqualTo("fallback");
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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(
        future -> vertx.setTimer(100, l -> future.fail("expected failure"))
      ).onComplete(ar -> result.set(ar.result()));
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    assertThat(called.get()).isEqualTo(false);

    AtomicBoolean spy = new AtomicBoolean();
    breaker.<String>execute(
      future -> vertx.setTimer(100, l -> {
        future.fail("expected failure");
        spy.set(true);
      }))
      .onComplete(ar -> result.set(ar.result()));
    await().untilAtomic(called, is(true));
    assertThat(spy.get()).isEqualTo(false);
    assertThat(result.get()).isEqualTo("fallback");
  }

  @Test
  public void testFailureOnAsynchronousCodeWithCustomPredicate() {
    AtomicBoolean called = new AtomicBoolean(false);
    AtomicReference<String> result = new AtomicReference<>();
    CircuitBreakerOptions options = new CircuitBreakerOptions().setResetTimeout(-1).setAsyncFailurePolicy(ar -> {
      if(ar.failed() && ar.cause() instanceof NoStackTraceThrowable) {
        return true;
      }

      return false;
    });
    breaker = CircuitBreaker.create("test", vertx, options)
      .fallback(v -> {
        called.set(true);
        return "fallback";
      });
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(
        future -> vertx.setTimer(100, l -> future.fail("expected failure"))
      ).onComplete(ar -> result.set(ar.result()));
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    assertThat(called.get()).isFalse();

    AtomicBoolean spy = new AtomicBoolean();
    breaker.<String>execute(
        future -> vertx.setTimer(100, l -> {
          future.fail("expected failure");
          spy.set(true);
        }))
      .onComplete(ar -> {
        result.set(ar.result());
      });;
    await().untilAtomic(called, is(true));
    assertThat(spy.get()).isEqualTo(false);
    assertThat(result.get()).isEqualTo("fallback");
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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN  || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertThat(called.get()).isEqualTo(false);

    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);

    AtomicBoolean spy = new AtomicBoolean();
    breaker.execute(v -> {
      spy.set(true);
      v.complete();
    });
    assertThat(spy.get()).isEqualTo(true);
    assertThat(called.get()).isEqualTo(false);
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertThat(called.get()).isEqualTo(true);

    await().until(() -> breaker.state() == CircuitBreakerState.HALF_OPEN);
    called.set(false);

    AtomicReference<String> result = new AtomicReference<>();
    breaker.<String>execute(v -> {
      throw new RuntimeException("oh no, but this is expected");
    }).onComplete(ar -> result.set(ar.result()));

    await().until(called::get);
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertThat(result.get()).isEqualTo("fallback");
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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
    assertThat(called.get()).isEqualTo(false);
    assertThat(failureCount.get()).isEqualTo(options.getMaxFailures());

    AtomicBoolean spy = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();
    breaker.<String>execute(v -> {
      spy.set(true);
      v.complete();
    })
      .onComplete(ar -> result.set(ar.result()));
    assertThat(spy.get()).isEqualTo(false);
    assertThat(called.get()).isEqualTo(true);
    assertThat(result.get()).isEqualTo("fallback");
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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
    assertThat(called.get()).isEqualTo(true);
    assertThat(count.get()).isEqualTo(options.getMaxFailures() + 3);
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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(future -> {
        // Do nothing with the future, this is a very bad thing.
      });
    }
    await().untilAtomic(hasBeenOpened, is(true));
    assertThat(called.get()).isEqualTo(true);

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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(future -> {
        // Do nothing with the future, this is a very bad thing.
      });
    }
    await().untilAtomic(hasBeenOpened, is(true));
    assertThat(called.get()).isEqualTo(true);
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
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(future -> future.fail("expected failure"));
    }
    await().untilAtomic(hasBeenOpened, is(true));
    assertThat(called.get()).isEqualTo(true);

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
    assertThat(fallbackCalled.get()).isEqualTo(options.getMaxFailures() - 1);
  }

  @Test
  public void testFailureWhenThereIsNoFallback() {
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(50000)
      .setTimeout(300)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options);

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    List<AsyncResult<String>> results = new ArrayList<>();
    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(future -> future.fail("expected failure"))
        .onComplete(results::add);
    }
    await().until(() -> results.size() == options.getMaxFailures());
    results.forEach(ar -> {
      assertThat(ar.failed()).isTrue();
      assertThat(ar.cause()).isNotNull().hasMessage("expected failure");
    });

    results.clear();

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    breaker.<String>execute(future -> future.fail("expected failure"))
      .onComplete(results::add);
    await().until(() -> results.size() == 1);
    results.forEach(ar -> {
      assertThat(ar.failed()).isTrue();
      assertThat(ar.cause()).isNotNull()
        .isInstanceOf(OpenCircuitException.class)
        .hasMessage("open circuit");
    });

    ((CircuitBreakerImpl) breaker).reset(true);

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    results.clear();

    breaker.<String>execute(future -> {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        // Ignored.
      }
    })
      .onComplete(results::add);
    await().until(() -> results.size() == 1);
    results.forEach(ar -> {
      assertThat(ar.failed()).isTrue();
      assertThat(ar.cause()).isInstanceOf(TimeoutException.class);
      assertThat(ar.cause()).isNotNull().hasMessage("operation timeout");
    });
  }

  @Test
  public void testWhenFallbackThrowsAnException() {
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setResetTimeout(5000)
      .setFallbackOnFailure(true);
    breaker = CircuitBreaker.create("test", vertx, options);

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    List<AsyncResult<String>> results = new ArrayList<>();
    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>executeWithFallback(
        future -> future.fail("expected failure"),
        t -> {
          throw new RuntimeException("boom");
        })
        .onComplete(results::add);
    }
    await().until(() -> results.size() == options.getMaxFailures());
    results.forEach(ar -> {
      assertThat(ar.failed()).isTrue();
      assertThat(ar.cause()).isNotNull().hasMessage("boom");
    });

    results.clear();

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    breaker.<String>executeWithFallback(
      future -> future.fail("expected failure"),
      t -> {
        throw new RuntimeException("boom");
      })
      .onComplete(results::add);
    await().until(() -> results.size() == 1);
    results.forEach(ar -> {
      assertThat(ar.failed()).isTrue();
      assertThat(ar.cause()).isNotNull().hasMessage("boom");
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

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.<String>execute(future -> future.fail("expected failure"));
    }
    await().until(() -> failures.size() == options.getMaxFailures());
    failures.forEach(ar -> assertThat(ar).isNotNull().hasMessage("expected failure"));

    failures.clear();

    ((CircuitBreakerImpl) breaker).reset(true);

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    failures.clear();

    breaker.<String>execute(future -> {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        // Ignored.
      }
    });
    await().until(() -> failures.size() == 1);
    failures.forEach(ar -> assertThat(ar).isNotNull()
      .isInstanceOf(TimeoutException.class)
      .hasMessage("operation timeout"));

    ((CircuitBreakerImpl) breaker).reset(true);

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    failures.clear();

    breaker.<String>execute(future -> {
      throw new RuntimeException("boom");
    });
    await().until(() -> failures.size() == 1);
    failures.forEach(ar -> assertThat(ar).isNotNull().hasMessage("boom"));
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
    assertThat(result.get().failed()).isTrue();
    assertThat(breaker.failureCount()).isEqualTo(1);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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
    assertThat(result.get().succeeded()).isTrue();
    assertThat(breaker.failureCount()).isEqualTo(0);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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
    assertThat(breaker.failureCount()).isGreaterThanOrEqualTo(options.getMaxFailures() + 1);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);


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
    failures.forEach(ar -> assertThat(ar).isNotNull()
      .isInstanceOf(TimeoutException.class)
      .hasMessage("operation timeout"));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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
    assertThat(result.get().succeeded()).isTrue();
    assertThat(breaker.failureCount()).isEqualTo(0);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

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
