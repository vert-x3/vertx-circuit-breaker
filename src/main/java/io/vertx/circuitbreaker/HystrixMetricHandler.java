package io.vertx.circuitbreaker;

import io.vertx.circuitbreaker.impl.HystrixMetricEventStream;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

/**
 * A Vert.x web handler to expose the circuit breaker to the Hystrix dasbboard.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@VertxGen
public interface HystrixMetricHandler extends Handler<RoutingContext> {

  /**
   * Creates the handler.
   *
   * @param vertx   the Vert.x instance
   * @param options the circuit breaker options.
   * @return the handler
   */
  static HystrixMetricHandler create(Vertx vertx, CircuitBreakerOptions options) {
    return new HystrixMetricEventStream(vertx, options);
  }

}
