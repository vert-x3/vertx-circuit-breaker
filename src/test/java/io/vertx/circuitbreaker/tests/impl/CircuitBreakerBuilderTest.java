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

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.core.Vertx;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;

public class CircuitBreakerBuilderTest {

  private CircuitBreaker breaker;
  private Vertx vertx;

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
  public void testBuiltBreakerUsesConfiguredOptionsAndInvokesHandler() {
    AtomicInteger openCount = new AtomicInteger();

    breaker = CircuitBreaker.builder("test", vertx)
      .with(new CircuitBreakerOptions().setMaxFailures(1).setResetTimeout(-1))
      .openHandler(v -> openCount.incrementAndGet())
      .build();

    assertEquals("test", breaker.name());
    assertEquals(CircuitBreakerState.CLOSED, breaker.state());

    breaker.open();

    await().untilAtomic(openCount, is(1));
    assertEquals(CircuitBreakerState.OPEN, breaker.state());
  }

  @Test(expected = IllegalStateException.class)
  public void testHandlerSetThroughBuilderCannotBeChanged() {
    breaker = CircuitBreaker.builder("test", vertx)
      .openHandler(v -> {
      })
      .build();

    breaker.openHandler(v -> {
    });
  }

  @Test
  public void testHandlerNotSetThroughBuilderStaysMutable() {
    breaker = CircuitBreaker.builder("test", vertx)
      .openHandler(v -> {
      })
      .build();

    // closeHandler was never supplied to the builder, so it should remain freely settable, more than once.
    breaker.closeHandler(v -> {
    });
    breaker.closeHandler(v -> {
    });
  }

  @Test
  public void testLegacyCreateKeepsHandlersMutable() {
    AtomicInteger openCount = new AtomicInteger();

    breaker = CircuitBreaker.create("test", vertx);
    breaker.openHandler(v -> openCount.set(1));
    breaker.openHandler(v -> openCount.set(2));

    breaker.open();

    await().untilAtomic(openCount, is(2));
  }
}
