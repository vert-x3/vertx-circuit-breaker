package io.vertx.circuitbreaker.tests.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.Repeat;
import io.vertx.ext.unit.junit.RepeatRule;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(VertxUnitRunner.class)
public class UsageTest {

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

//  @Rule
//  public WireMockRule wireMockRule = new WireMockRule(8089);
  private Vertx vertx;
  private CircuitBreaker cb;

  @Before
  public void setUp() throws Exception {
    vertx = Vertx.vertx();
    items.clear();
    cb = CircuitBreaker.create("circuit-breaker", vertx, new CircuitBreakerOptions()
        .setFallbackOnFailure(true)
        .setTimeout(500)
        .setResetTimeout(1000));

    vertx.eventBus().consumer("ok", message -> message.reply("OK"));

    vertx.eventBus().consumer("fail", message -> message.fail(100, "Bad bad bad"));

    vertx.eventBus().consumer("exception", message -> {
      throw new RuntimeException("RT - Bad bad bad");
    });

    vertx.eventBus().consumer("timeout", message -> vertx.setTimer(2000, x -> message.reply("Too late")));
  }

  @After
  public void tearDown() {
    cb.close();
    vertx.close();
  }

  @Test
  @Repeat(10)
  public void testCBWithReadOperation() throws Exception {
    vertx.createHttpServer().requestHandler(req -> {
        switch (req.path()) {
          case "/resource":
            req.response()
              .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
              .end(new JsonObject().put("status", "OK").encode());
            break;
          case "/delayed":
            vertx.setTimer(2000, id -> {
              req.response().end();
            });
            break;
          case "/error":
            req.response()
              .setStatusCode(500)
              .end("This is an error");
            break;
        }
      }).listen(8089)
      .await(20, TimeUnit.SECONDS);


    HttpClient client = vertx.createHttpClient();

    AtomicReference<JsonObject> json = new AtomicReference<>();
    cb.<JsonObject>executeWithFallback(
        promise -> {
          client.request(HttpMethod.GET, 8089, "localhost", "/resource")
            .compose(req -> req
              .putHeader("Accept", "application/json")
              .send().compose(resp -> resp
                .body()
                .map(Buffer::toJsonObject))
            ).onComplete(promise);
        },
        t -> null
    ).onComplete(ar -> json.set(ar.result()));
    await().atMost(1, TimeUnit.MINUTES).untilAtomic(json, is(notNullValue()));
    assertEquals("OK", json.get().getString("status"));

    json.set(null);
    cb.executeWithFallback(
        promise -> {
          client.request(HttpMethod.GET, 8089, "localhost", "/error")
            .compose(req -> req
              .putHeader("Accept", "application/json")
              .send().compose(resp -> {
                if (resp.statusCode() != 200) {
                  return Future.failedFuture("Invalid response");
                } else {
                  return resp.body().map(Buffer::toJsonObject);
                }
              })
            ).onComplete(promise);
        },
        t -> new JsonObject().put("status", "KO")
    ).onComplete(ar -> json.set(ar.result()));
    await().untilAtomic(json, is(notNullValue()));
    assertEquals("KO", json.get().getString("status"));

    json.set(null);
    cb.executeWithFallback(
        promise -> {
          client.request(HttpMethod.GET, 8089, "localhost", "/delayed")
            .compose(req -> req
              .putHeader("Accept", "application/json")
              .send().compose(resp -> {
                if (resp.statusCode() != 200) {
                  return Future.failedFuture("Invalid response");
                } else {
                  return resp.body().map(Buffer::toJsonObject);
                }
              })
            ).onComplete(promise);
        },
        t -> new JsonObject().put("status", "KO")
    ).onComplete(ar -> json.set(ar.result()));
    await().untilAtomic(json, is(notNullValue()));
    assertEquals("KO", json.get().getString("status"));
  }

  private List<String> items = new ArrayList<>();

  public void asyncWrite(String content, Scenario scenario, Completable<Void> resultHandler) {
    long random = (long) (Math.random() * 1000);
    switch (scenario) {
      case TIMEOUT:
        random = 2000;
        break;
      case RUNTIME_EXCEPTION:
        throw new RuntimeException("Bad bad bad");
    }


    vertx.setTimer(random, l -> {
      if (scenario == Scenario.FAILURE) {
        synchronized (UsageTest.this) {
          items.add("Error");
        }
        resultHandler.fail("Bad Bad Bad");
      } else {
        synchronized (UsageTest.this) {
          items.add(content);
        }
        resultHandler.succeed();
      }
    });
  }

  enum Scenario {
    OK,
    FAILURE,
    RUNTIME_EXCEPTION,
    TIMEOUT
  }

  @Test
  public void testCBWithWriteOperation() {
    cb.<Void>executeWithFallback(
        future -> {
          asyncWrite("Hello", Scenario.OK, future);
        },
        t -> null
    );

    await().until(() -> {
      synchronized (UsageTest.this) {
        return items.size() == 1;
      }
    });
    items.clear();

    AtomicBoolean fallbackCalled = new AtomicBoolean();
    cb.<Void>executeWithFallback(
        future -> {
          asyncWrite("Hello", Scenario.FAILURE, future);
        },
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().until(() -> {
      synchronized (UsageTest.this) {
        return items.size() == 1;
      }
    });

    assertTrue(fallbackCalled.get());

    items.clear();
    fallbackCalled.set(false);

    cb.<Void>executeWithFallback(
        future -> asyncWrite("Hello", Scenario.TIMEOUT, future),
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().untilAtomic(fallbackCalled, is(true));
    assertEquals(Collections.emptyList(), items);

    items.clear();
    fallbackCalled.set(false);
    cb.<Void>executeWithFallback(
        future -> asyncWrite("Hello", Scenario.RUNTIME_EXCEPTION, future),
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().untilAtomic(fallbackCalled, is(true));
    assertEquals(Collections.emptyList(), items);
  }


  @Test
  public void testCBWithEventBus() {
    cb.<Message<String>>executeWithFallback(
        future -> vertx.eventBus().<String>request("ok", "").onComplete(future),
        t -> null
    ).onComplete(ar -> items.add(ar.result().body()));

    await().until(() -> {
      synchronized (UsageTest.this) {
        return items.size() == 1;
      }
    });
    items.clear();

    AtomicBoolean fallbackCalled = new AtomicBoolean();
    cb.<Message<String>>executeWithFallback(
        future -> vertx.eventBus().<String>request("timeout", "").onComplete(future),
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().untilAtomic(fallbackCalled, is(true));
    assertEquals(Collections.emptyList(), items);
    fallbackCalled.set(false);

    cb.<Message<String>>executeWithFallback(
        future -> vertx.eventBus().<String>request("fail", "").onComplete(future),
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().untilAtomic(fallbackCalled, is(true));
    assertEquals(Collections.emptyList(), items);
    fallbackCalled.set(false);

    cb.<Message<String>>executeWithFallback(
        future -> vertx.eventBus().<String>request("exception", "").onComplete(future),
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().untilAtomic(fallbackCalled, is(true));
    assertEquals(Collections.emptyList(), items);
    fallbackCalled.set(false);
  }
}
