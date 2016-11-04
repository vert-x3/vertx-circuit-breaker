package io.vertx.groovy.circuitbreaker;
public class GroovyStaticExtension {
  public static io.vertx.circuitbreaker.CircuitBreaker create(io.vertx.circuitbreaker.CircuitBreaker j_receiver, java.lang.String name, io.vertx.core.Vertx vertx, java.util.Map<String, Object> options) {
    return io.vertx.lang.groovy.ConversionHelper.wrap(io.vertx.circuitbreaker.CircuitBreaker.create(name,
      vertx,
      options != null ? new io.vertx.circuitbreaker.CircuitBreakerOptions(io.vertx.lang.groovy.ConversionHelper.toJsonObject(options)) : null));
  }
}
