package io.vertx.circuitbreaker.tests.impl;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Repeat;
import io.vertx.ext.unit.junit.RepeatRule;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@RunWith(VertxUnitRunner.class)
public class UsageTest {

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  private Vertx vertx;
  private CircuitBreaker cb;
  private HttpServer server;
  private HttpClient client;

  @Before
  public void setUp() {
    vertx = Vertx.vertx();
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
    client = vertx.createHttpClient();
  }

  @After
  public void tearDown() {
    if (server != null) {
      server.close().await();
    }
    if (client != null) {
      client.close().await();
    }
    cb.close();
    vertx.close().await();
  }

  @Ignore
  @Test
  @Repeat(10)
  public void testCBWithReadOperation1(TestContext should) throws Exception {
    server = vertx.createHttpServer().requestHandler(req -> {
        req.response()
          .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
          .end(new JsonObject().put("status", "OK").encode());
      }).listen(8089)
      .await(20, TimeUnit.SECONDS);
    Async async = should.async();
    vertx.runOnContext(v -> {
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
        t -> {
          should.fail(t);
          return null;
        }
      ).onComplete(should.asyncAssertSuccess(json -> {
        should.assertEquals("OK", json.getString("status"));
        async.complete();
      }));
    });
  }

  @Ignore
  @Test
  @Repeat(10)
  public void testCBWithReadOperation2(TestContext should) throws Exception {
    server = vertx.createHttpServer().requestHandler(req -> {
        req.response()
          .setStatusCode(500)
          .end("This is an error");
      }).listen(8089)
      .await(20, TimeUnit.SECONDS);
    Async async = should.async();
    vertx.runOnContext(v -> {
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
      ).onComplete(should.asyncAssertSuccess(json -> {
        should.assertEquals("KO", json.getString("status"));
        async.complete();
      }));
    });
  }

  @Ignore
  @Test
  @Repeat(10)
  public void testCBWithReadOperation3(TestContext should) throws Exception {
    server = vertx.createHttpServer().requestHandler(req -> {
        vertx.setTimer(2000, id -> {
          req.response().end();
        });
      }).listen(8089)
      .await(20, TimeUnit.SECONDS);
    Async async = should.async();
    vertx.runOnContext(v -> {
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
      ).onComplete(should.asyncAssertSuccess(json -> {
        should.assertEquals("KO", json.getString("status"));
        async.complete();
      }));
    });
  }

  private void asyncWrite(Scenario scenario, Promise<String> promise) {
    long delay;
    switch (scenario) {
      case RUNTIME_EXCEPTION:
        throw new RuntimeException("Bad bad bad");
      case TIMEOUT:
        delay = 2000;
        break;
      default:
        delay = ThreadLocalRandom.current().nextLong(1, 250); // Must be less than CB timeout
        break;
    }

    vertx.setTimer(delay, l -> {
      if (scenario == Scenario.FAILURE) {
        promise.fail("Bad Bad Bad");
      } else {
        promise.succeed("foo");
      }
    });
  }

  private enum Scenario {
    OK,
    FAILURE,
    RUNTIME_EXCEPTION,
    TIMEOUT
  }

  @Test
  @Repeat(10)
  public void testCBWithWriteOperation() {
    String str = cb.executeWithFallback(
      promise -> asyncWrite(Scenario.OK, promise),
      t -> "bar"
    ).await();
    assertEquals("foo", str);

    str = cb.executeWithFallback(
      promise -> asyncWrite(Scenario.FAILURE, promise),
      t -> "bar"
    ).await();
    assertEquals("bar", str);

    str = cb.executeWithFallback(
      promise -> asyncWrite(Scenario.TIMEOUT, promise),
      t -> "bar"
    ).await();
    assertEquals("bar", str);

    str = cb.executeWithFallback(
      promise -> asyncWrite(Scenario.RUNTIME_EXCEPTION, promise),
      t -> "bar"
    ).await();
    assertEquals("bar", str);
  }


  @Test
  public void testCBWithEventBus() {
    String str = cb.executeWithFallback(
      promise -> vertx.eventBus().<String>request("ok", "").map(Message::body).onComplete(promise),
      t -> "KO"
    ).await();
    assertEquals("OK", str);

    str = cb.executeWithFallback(
      promise -> vertx.eventBus().<String>request("timeout", "").map(Message::body).onComplete(promise),
      t -> "KO"
    ).await();
    assertEquals("KO", str);

    str = cb.executeWithFallback(
      promise -> vertx.eventBus().<String>request("fail", "").map(Message::body).onComplete(promise),
      t -> "KO"
    ).await();
    assertEquals("KO", str);

    str = cb.executeWithFallback(
      promise -> vertx.eventBus().<String>request("exception", "").map(Message::body).onComplete(promise),
      t -> "KO"
    ).await();
    assertEquals("KO", str);
  }
}
