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

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
    }).setHandler(ar -> completionCalled.set(ar.result()));

    await().until(operationCalled::get);
    await().until(() -> completionCalled.get().equalsIgnoreCase("hello"));
  }

  @Test
  @Repeat(5)
  public void testWithUserFutureOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    AtomicBoolean operationCalled = new AtomicBoolean();
    AtomicReference<String> completionCalled = new AtomicReference<>();

    Future<String> userFuture = Future.future();
    userFuture.setHandler(ar ->
      completionCalled.set(ar.result()));

    breaker.executeAndReport(userFuture, fut -> {
      operationCalled.set(true);
      fut.complete("hello");
    });

    await().until(operationCalled::get);
    await().until(() ->  completionCalled.get().equalsIgnoreCase("hello"));
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
    ).setHandler(ar -> result.set(ar.result()));

    await().until(called::get);
    await().untilAtomic(result, is("hello"));
  }

  @Test
  public void testAsynchronousWithUserFutureOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    AtomicBoolean called = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();

    Future<String> userFuture = Future.future();
    userFuture.setHandler(ar -> result.set(ar.result()));

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
      .setHandler(ar -> lastException.set(ar.cause()));

    assertThat(spyOpen.get()).isEqualTo(0);
    assertThat(spyClosed.get()).isEqualTo(0);
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    assertThat(lastException.get()).isNotNull();
    lastException.set(null);

    for (int i = 1; i < CircuitBreakerOptions.DEFAULT_MAX_FAILURES; i++) {
      breaker.execute(v -> {
        throw new RuntimeException("oh no, but this is expected");
      })
        .setHandler(ar -> lastException.set(ar.cause()));
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN || breaker.state() == CircuitBreakerState.HALF_OPEN);
    assertThat(spyOpen.get()).isEqualTo(1);
    assertThat(lastException.get()).isNotNull();

    breaker.reset();
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    assertThat(spyOpen.get()).isEqualTo(1);
    assertThat(spyClosed.get()).isEqualTo(1);
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
      Future<String> future = Future.future();
      AtomicReference<String> result = new AtomicReference<>();
      breaker.executeAndReport(future, v -> {
        throw new RuntimeException("oh no, but this is expected");
      });
      future.setHandler(ar -> result.set(ar.result()));
      assertThat(result.get()).isNull();
    }

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);

    AtomicBoolean spy = new AtomicBoolean();
    AtomicReference<String> result = new AtomicReference<>();
    Future<String> fut = Future.future();
    fut.setHandler(ar ->
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
      ).setHandler(ar -> result.set(ar.result()));
    }
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    assertThat(called.get()).isEqualTo(false);

    AtomicBoolean spy = new AtomicBoolean();
    breaker.<String>execute(
      future -> vertx.setTimer(100, l -> {
        future.fail("expected failure");
        spy.set(true);
      }))
      .setHandler(ar -> result.set(ar.result()));
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
    }).setHandler(ar -> result.set(ar.result()));

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
      }).setHandler(ar -> {
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
      .setHandler(ar -> result.set(ar.result()));
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
      }).setHandler(ar -> {
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

    breaker.execute(Future::complete);
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    await().untilAtomic(called, is(false));
  }

  @Test
  @Repeat(5)
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
    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
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
    breaker.execute(Future::complete);
    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    called.set(false);
    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(f -> f.complete(null));
    }

    await().until(() -> breaker.state() == CircuitBreakerState.CLOSED);
    await().untilAtomic(called, is(false));
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
        .setHandler(results::add);
    }
    await().until(() -> results.size() == options.getMaxFailures());
    results.stream().forEach(ar -> {
      assertThat(ar.failed()).isTrue();
      assertThat(ar.cause()).isNotNull().hasMessage("expected failure");
    });

    results.clear();

    await().until(() -> breaker.state() == CircuitBreakerState.OPEN);
    breaker.<String>execute(future -> future.fail("expected failure"))
      .setHandler(results::add);
    await().until(() -> results.size() == 1);
    results.stream().forEach(ar -> {
      assertThat(ar.failed()).isTrue();
      assertThat(ar.cause()).isNotNull().hasMessage("open circuit");
    });

    breaker.reset();

    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
    results.clear();

    breaker.<String>execute(future -> {
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        // Ignored.
      }
    })
      .setHandler(results::add);
    await().until(() -> results.size() == 1);
    results.stream().forEach(ar -> {
      assertThat(ar.failed()).isTrue();
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
        .setHandler(results::add);
    }
    await().until(() -> results.size() == options.getMaxFailures());
    results.stream().forEach(ar -> {
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
      .setHandler(results::add);
    await().until(() -> results.size() == 1);
    results.stream().forEach(ar -> {
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
    failures.stream().forEach(ar -> assertThat(ar).isNotNull().hasMessage("expected failure"));

    failures.clear();

    breaker.reset();

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
    failures.stream().forEach(ar -> assertThat(ar).isNotNull().hasMessage("operation timeout"));

    breaker.reset();

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

    await().untilAtomic(calls, is(5));
    assertThat(result.get().failed()).isTrue();
    assertThat(breaker.failureCount()).isEqualTo(1);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    breaker.reset();
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

    breaker.reset();
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
    breaker.reset();
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


    breaker.reset();
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
    failures.forEach(ar -> assertThat(ar).isNotNull().hasMessage("operation timeout"));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    breaker.reset();
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

}
