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
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jayway.awaitility.Awaitility.*;
import static io.vertx.core.http.HttpHeaders.*;
import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.core.Is.*;
import static org.junit.Assert.*;

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
    AtomicBoolean invoked = new AtomicBoolean();
    router.route(HttpMethod.GET, "/flaky").handler(ctxt -> {
      if (invoked.compareAndSet(false, true)) {
        ctxt.response().setStatusCode(503).putHeader(RETRY_AFTER, "2").end();
      } else {
        ctxt.response().setStatusCode(200).end();
      }
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
    breaker.executeAndReport(result, v -> client.request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(req -> req
          .send()
          .compose(resp -> resp
            .body()
            .map(Buffer::toString)))
      .onComplete(v));

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
          client.request(HttpMethod.GET, 8080, "localhost", "/error").compose(req ->
            req.send().compose(resp -> Future.succeededFuture(resp.statusCode()))
          ).onSuccess(sc -> {
            if (sc != 200) {
              future.fail("http error");
            } else {
              future.complete();
            }
            count.incrementAndGet();
          })
      );
    }

    await().untilAtomic(count, is(options.getMaxFailures()));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);

    Promise<String> result = Promise.promise();
    breaker.executeAndReportWithFallback(result, future ->
      client.request(HttpMethod.GET, 8080, "localhost", "/error")
        .compose(req -> req.send().compose(resp -> Future.succeededFuture(resp.statusCode())))
      .onSuccess(sc -> {
        if (sc != 200) {
          future.fail("http error");
        } else {
          future.complete();
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
          client.request(HttpMethod.GET,8080, "localhost", "/long").compose(req ->
            req.send().compose(HttpClientResponse::body).onSuccess(body -> {
            count.incrementAndGet();
            future.complete();
          })));
    }

    await().untilAtomic(count, is(options.getMaxFailures()));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);

    Promise<String> result = Promise.promise();
    breaker.executeAndReportWithFallback(result, future ->
      client.request(HttpMethod.GET, 8080, "localhost", "/long")
        .compose(HttpClientRequest::send).onSuccess(response -> {
          System.out.println("Got response");
          future.complete();
        }), v -> "fallback");

    await().until(() -> result.future().result().equals("fallback"));
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.OPEN);
  }

  private static class ServiceUnavailableException extends VertxException {
    final int delay;

    ServiceUnavailableException(int delay) {
      super("unavailable", true);
      this.delay = delay;
    }
  }

  @Test
  public void testUseRetryAfterHeaderValue() {
    breaker = CircuitBreaker.create("test", vertx, new CircuitBreakerOptions().setMaxRetries(1))
      .retryPolicy((failure, retryCount) -> {
        if (failure instanceof ServiceUnavailableException) {
          ServiceUnavailableException sue = (ServiceUnavailableException) failure;
          return MILLISECONDS.convert(sue.delay, SECONDS);
        }
        return 0;
      });
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    List<LocalDateTime> requestLocalDateTimes = Collections.synchronizedList(new ArrayList<>());
    Promise<String> result = Promise.promise();
    breaker.executeAndReport(result, v -> {
      requestLocalDateTimes.add(LocalDateTime.now());
      client.request(HttpMethod.GET, 8080, "localhost", "/flaky")
        .compose(req -> req
          .send()
          .compose(resp -> {
            if (resp.statusCode() == 503) {
              ServiceUnavailableException sue = new ServiceUnavailableException(Integer.parseInt(resp.getHeader(RETRY_AFTER)));
              return Future.failedFuture(sue);
            } else {
              return resp.body().map(Buffer::toString);
            }
          })
        )
        .onComplete(v);
    });

    await().until(() -> result.future().result() != null);
    assertThat(breaker.state()).isEqualTo(CircuitBreakerState.CLOSED);

    assertEquals(2, requestLocalDateTimes.size());
    assertTrue(Duration.between(requestLocalDateTimes.get(0), requestLocalDateTimes.get(1)).toMillis() >= 2000);
  }

}
