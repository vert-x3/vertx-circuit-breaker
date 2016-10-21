package io.vertx.circuitbreaker.metrics;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.HystrixMetricHandler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class DashboardExample {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    CircuitBreakerOptions options = new CircuitBreakerOptions()
        .setFallbackOnFailure(true)
        .setMaxFailures(10)
        .setResetTimeout(5000)
        .setTimeout(1000)
        .setMetricsRollingWindow(10000);

    CircuitBreaker cb = CircuitBreaker.create("some-name", vertx, options);

    Router router = Router.router(vertx);
    router.get("/metrics").handler(HystrixMetricHandler.create(vertx, options));
    router.get("/ok").handler(rc -> ok(rc, cb));
    router.get("/timeout").handler(rc -> timeout(rc, cb));
    router.get("/failure").handler(rc -> failure(rc, cb));
    router.get("/error").handler(rc -> error(rc, cb));

    vertx.createHttpServer()
        .requestHandler(router::accept)
        .listen(8080);
  }

  private static void ok(RoutingContext req, CircuitBreaker cb) {
    cb.executeWithFallback(
        future -> future.complete("OK"),
        t -> "OK (fallback)")
        .setHandler(ar -> req.response().end(ar.result()));
  }

  private static void timeout(RoutingContext req, CircuitBreaker cb) {
    cb.executeWithFallback(
        future -> {
        },
        t -> "OK (fallback - timeout)")
        .setHandler(ar -> req.response().end(ar.result()));
  }

  private static void failure(RoutingContext req, CircuitBreaker cb) {
    cb.executeWithFallback(
        future -> future.fail("KO"),
        t -> "OK (fallback - failure)")
        .setHandler(ar -> {
          req.response().end(ar.result());
        });
  }

  private static void error(RoutingContext req, CircuitBreaker cb) {
    cb.executeWithFallback(
        future -> {
          throw new NullPointerException("bad bad bad");
        },
        t -> "OK (fallback - error)")
        .setHandler(ar -> {
          req.response().end(ar.result());
        });
  }

}
