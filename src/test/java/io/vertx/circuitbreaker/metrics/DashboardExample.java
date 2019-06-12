package io.vertx.circuitbreaker.metrics;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.HystrixMetricHandler;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.Random;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class DashboardExample {

  private static Random random = new Random();

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    CircuitBreakerOptions options = new CircuitBreakerOptions()
      .setFallbackOnFailure(true)
      .setMaxFailures(10)
      .setResetTimeout(5000)
      .setTimeout(1000)
      .setMetricsRollingWindow(10000);

    CircuitBreaker cba = CircuitBreaker.create("A", vertx, options);
    CircuitBreaker cbb = CircuitBreaker.create("B", vertx, options);
    CircuitBreaker cbc = CircuitBreaker.create("C", vertx, options);

    Router router = Router.router(vertx);
    router.get("/metrics").handler(HystrixMetricHandler.create(vertx));


    router.get("/A").handler(rc -> a(rc, cba));
    router.get("/B").handler(rc -> b(rc, cbb));
    router.get("/C").handler(rc -> c(rc, cbc));

    vertx.createHttpServer()
      .requestHandler(router::accept)
      .listen(8080);
  }


  private static void a(RoutingContext rc, CircuitBreaker cb) {
    int choice = random.nextInt(10);
    if (choice < 7) {
      cb.executeWithFallback(
        commandThatWorks(rc.vertx()),
        (t) -> "OK (fallback)")
        .setHandler(s -> rc.response().end(s.result()));
    } else {
      cb.executeWithFallback(
        commandThatFails(rc.vertx()),
        (t) -> "OK (fallback)")
        .setHandler(s -> rc.response().end(s.result()));
    }
  }

  private static void b(RoutingContext rc, CircuitBreaker cb) {
    int choice = random.nextInt(10);
    if (choice < 5) {
      cb.executeWithFallback(
        commandThatWorks(rc.vertx()),
        (t) -> "OK (fallback)")
        .setHandler(s -> rc.response().end(s.result()));
    } else if (choice < 7) {
      cb.executeWithFallback(
        commandThatCrashes(rc.vertx()),
        (t) -> "OK (fallback)")
        .setHandler(s -> rc.response().end(s.result()));
    } else {
      cb.executeWithFallback(
        commandThatFails(rc.vertx()),
        (t) -> "OK (fallback)")
        .setHandler(s -> rc.response().end(s.result()));
    }
  }

  private static void c(RoutingContext rc, CircuitBreaker cb) {
    int choice = random.nextInt(10);
    if (choice < 5) {
      cb.executeWithFallback(
        commandThatWorks(rc.vertx()),
        (t) -> "OK (fallback)")
        .setHandler(s -> rc.response().end(s.result()));
    } else if (choice < 7) {
      cb.executeWithFallback(
        commandThatTimeout(rc.vertx(), 15000),
        (t) -> "OK (fallback)")
        .setHandler(s -> rc.response().end(s.result()));
    } else {
      cb.executeWithFallback(
        commandThatFails(rc.vertx()),
        (t) -> "OK (fallback)")
        .setHandler(s -> rc.response().end(s.result()));
    }
  }

  private static Handler<Promise<String>> commandThatWorks(Vertx vertx) {
    return (future -> vertx.setTimer(5, l -> future.complete("OK !")));
  }

  private static Handler<Promise<String>> commandThatFails(Vertx vertx) {
    return (future -> vertx.setTimer(5, l -> future.fail("expected failure")));
  }

  private static Handler<Promise<String>> commandThatCrashes(Vertx vertx) {
    return (future -> {
      throw new RuntimeException("Expected error");
    });
  }

  private static Handler<Promise<String>> commandThatTimeout(Vertx vertx, int timeout) {
    return (future -> vertx.setTimer(timeout + 500, l -> future.complete("Is it too late ?")));
  }

  private static Handler<Promise<String>> commandThatTimeoutAndFail(Vertx vertx, int timeout) {
    return (future -> vertx.setTimer(timeout + 500, l -> future.fail("late failure")));
  }

}
