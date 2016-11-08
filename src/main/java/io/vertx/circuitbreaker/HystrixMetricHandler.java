package io.vertx.circuitbreaker;

import io.vertx.circuitbreaker.impl.HystrixMetricEventStream;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

/**
 * A Vert.x web handler to expose the circuit breaker to the Hystrix dasbboard. The handler listens to the circuit
 * breaker notifications sent on the event bus.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
@VertxGen
public interface HystrixMetricHandler extends Handler<RoutingContext> {

  /**
   * Creates the handler, using the default notification address.
   *
   * @param vertx the Vert.x instance
   * @return the handler
   */
  static HystrixMetricHandler create(Vertx vertx) {
    return new HystrixMetricEventStream(vertx, CircuitBreakerOptions.DEFAULT_NOTIFICATION_ADDRESS);
  }

  /**
   * Creates the handler.
   *
   * @param vertx   the Vert.x instance
   * @param address the address to listen on the event bus
   * @return the handler
   */
  static HystrixMetricHandler create(Vertx vertx, String address) {
    return new HystrixMetricEventStream(vertx, address);
  }

}
