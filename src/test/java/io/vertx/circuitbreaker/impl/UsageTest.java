package io.vertx.circuitbreaker.impl;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpClient;
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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(VertxUnitRunner.class)
public class UsageTest {

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(8089);
  private Vertx vertx;
  private CircuitBreaker cb;

  @Before
  public void setUp() {
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
  public void testCBWithReadOperation() {
    prepareHttpServer();

    HttpClient client = vertx.createHttpClient();

    AtomicReference<JsonObject> json = new AtomicReference<>();
    cb.<JsonObject>executeWithFallback(
        future -> {
          client.get(8089, "localhost", "/resource")
              .handler(response -> {
                response.exceptionHandler(future::fail)
                    .bodyHandler(buffer -> {
                      future.complete(buffer.toJsonObject());
                    });
              })
              .putHeader("Accept", "application/json")
              .exceptionHandler(future::fail)
              .end();
        },
        t -> null
    ).setHandler(ar -> json.set(ar.result()));
    await().atMost(1, TimeUnit.MINUTES).untilAtomic(json, is(notNullValue()));
    assertThat(json.get().getString("status")).isEqualTo("OK");

    json.set(null);
    cb.executeWithFallback(
        future -> {
          client.get(8089, "localhost", "/error")
              .handler(response -> {
                response.exceptionHandler(future::fail);
                if (response.statusCode() != 200) {
                  future.fail("Invalid response");
                } else {
                  response.bodyHandler(buffer -> future.complete(buffer.toJsonObject()));
                }
              })
              .putHeader("Accept", "application/json")
              .exceptionHandler(future::fail)
              .end();
        },
        t -> new JsonObject().put("status", "KO")
    ).setHandler(ar -> json.set(ar.result()));
    await().untilAtomic(json, is(notNullValue()));
    assertThat(json.get().getString("status")).isEqualTo("KO");

    json.set(null);
    cb.executeWithFallback(
        future -> {
          client.get(8089, "localhost", "/delayed")
              .handler(response -> {
                response.exceptionHandler(future::fail);
                if (response.statusCode() != 200) {
                  future.fail("Invalid response");
                } else {
                  response.bodyHandler(buffer -> future.complete(buffer.toJsonObject()));
                }
              })
              .putHeader("Accept", "application/json")
              .exceptionHandler(future::fail)
              .end();
        },
        t -> new JsonObject().put("status", "KO")
    ).setHandler(ar -> json.set(ar.result()));
    await().untilAtomic(json, is(notNullValue()));
    assertThat(json.get().getString("status")).isEqualTo("KO");
  }

  private void prepareHttpServer() {
    stubFor(get(urlEqualTo("/resource"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"status\":\"OK\"}")));

    stubFor(get(urlEqualTo("/delayed")).willReturn(
        aResponse()
            .withStatus(200)
            .withFixedDelay(2000)));

    stubFor(get(urlEqualTo("/error")).willReturn(
        aResponse()
            .withStatus(500)
            .withBody("This is an error")));
  }

  private List<String> items = new ArrayList<>();

  public void asyncWrite(String content, Scenario scenario, Handler<AsyncResult<Void>> resultHandler) {
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
        resultHandler.handle(Future.failedFuture("Bad Bad Bad"));
      } else {
        synchronized (UsageTest.this) {
          items.add(content);
        }
        resultHandler.handle(Future.succeededFuture());
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

    assertThat(fallbackCalled.get()).isTrue();

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
    assertThat(items).isEmpty();

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
    assertThat(items).isEmpty();
  }


  @Test
  public void testCBWithEventBus() {
    cb.<Message<String>>executeWithFallback(
        future -> vertx.eventBus().send("ok", "", future),
        t -> null
    ).setHandler(ar -> items.add(ar.result().body()));

    await().until(() -> {
      synchronized (UsageTest.this) {
        return items.size() == 1;
      }
    });
    items.clear();

    AtomicBoolean fallbackCalled = new AtomicBoolean();
    cb.<Message<String>>executeWithFallback(
        future -> vertx.eventBus().send("timeout", "", future),
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().untilAtomic(fallbackCalled, is(true));
    assertThat(items).isEmpty();
    fallbackCalled.set(false);

    cb.<Message<String>>executeWithFallback(
        future -> vertx.eventBus().send("fail", "", future),
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().untilAtomic(fallbackCalled, is(true));
    assertThat(items).isEmpty();
    fallbackCalled.set(false);

    cb.<Message<String>>executeWithFallback(
        future -> vertx.eventBus().send("exception", "", future),
        t -> {
          fallbackCalled.set(true);
          return null;
        }
    );

    await().untilAtomic(fallbackCalled, is(true));
    assertThat(items).isEmpty();
    fallbackCalled.set(false);
  }

}
