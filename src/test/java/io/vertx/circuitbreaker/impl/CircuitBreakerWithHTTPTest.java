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

import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.ext.web.Router;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Test the circuit breaker when doing HTTP calls.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CircuitBreakerWithHTTPTest {
  private Vertx vertx;
  private HttpServer http;
  private HttpClient client;
  private CircuitBreaker breaker;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
    Router router = Router.router(vertx);
    router.route(HttpMethod.GET, "/").handler(ctxt ->
        ctxt.response().setStatusCode(200).end("hello")
    );
    router.route(HttpMethod.GET, "/error").handler(ctxt ->
        ctxt.response().setStatusCode(500).end("failed !")
    );
    router.route(HttpMethod.GET, "/long").handler(ctxt -> {
      try {
        Thread.sleep(2000);
      } catch (Exception e) {
        // Ignored.
      }
      ctxt.response().setStatusCode(200).end("hello");
    });

    AtomicBoolean done = new AtomicBoolean();
    http = vertx.createHttpServer().requestHandler(router).listen(8080, ar -> {
      done.set(ar.succeeded());
    });

    await().untilAtomic(done, is(true));

    client = vertx.createHttpClient();
  }

  @After
  public void tearDown() {
    if (breaker != null) {
      breaker.close();
    }
    AtomicBoolean completed = new AtomicBoolean();
    http.close(ar -> completed.set(true));
    await().untilAtomic(completed, is(true));

    completed.set(false);
    vertx.close((v) -> completed.set(true));
    await().untilAtomic(completed, is(true));

    client.close();
  }

  @Test
  public void testOk() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions());
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    Promise<String> result = Promise.promise();
    breaker.executeAndReport(result, v -> client.getNow(8080, "localhost", "/",
      ar -> {
        if (ar.succeeded()) {
          HttpClientResponse response = ar.result();
          response.bodyHandler(buffer -> v.complete(buffer.toString()));
        }
      }));

    await().until(() -> result.future().result() != null);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);
  }

  @Test
  public void testFailure() {
    CircuitBreakerOptions options = new CircuitBreakerOptions();
    breaker = CircuitBreaker.create("test", vertx, options);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    AtomicInteger count = new AtomicInteger();

    for (int i = 0; i < options.getMaxFailures(); i++) {
      Promise<String> result = Promise.promise();
      breaker.executeAndReport(result, future ->
          client.getNow(8080, "localhost", "/error", ar -> {
            if (ar.succeeded()) {
              HttpClientResponse response = ar.result();
              if (response.statusCode() != 200) {
                future.fail("http error");
              } else {
                future.complete();
              }
              count.incrementAndGet();
            }
          })
      );
    }

    await().untilAtomic(count, is(options.getMaxFailures()));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);

    Promise<String> result = Promise.promise();
    breaker.executeAndReportWithFallback(result, future ->
        client.getNow(8080, "localhost", "/error", ar -> {
          if (ar.succeeded()) {
            HttpClientResponse response = ar.result();
            if (response.statusCode() != 200) {
              future.fail("http error");
            } else {
              future.complete();
            }
          }
        }), v -> "fallback");

    await().until(() -> result.future().result().equals("fallback"));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);

  }

  @Test
  public void testTimeout() {
    CircuitBreakerOptions options = new CircuitBreakerOptions().setTimeout(100).setMaxFailures(2);
    breaker = CircuitBreaker.create("test", vertx, options);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    AtomicInteger count = new AtomicInteger();

    for (int i = 0; i < options.getMaxFailures(); i++) {
      breaker.execute(future ->
          client.getNow(8080, "localhost", "/long", response -> {
            count.incrementAndGet();
            future.complete();
          }));
    }

    await().untilAtomic(count, is(options.getMaxFailures()));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);

    Promise<String> result = Promise.promise();
    breaker.executeAndReportWithFallback(result, future ->
      client.getNow(8080, "localhost", "/long", response -> {
        System.out.println("Got response");
        future.complete();
      }), v -> "fallback");

    await().until(() -> result.future().result().equals("fallback"));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
  }


}
