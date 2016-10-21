package io.vertx.circuitbreaker;

import io.vertx.circuitbreaker.impl.HystrixMetricEventStream;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@VertxGen
public interface HystrixMetricHandler extends Handler<RoutingContext> {

  static HystrixMetricHandler create(Vertx vertx, CircuitBreakerOptions options) {
    return new HystrixMetricEventStream(vertx, options);
  }

}
