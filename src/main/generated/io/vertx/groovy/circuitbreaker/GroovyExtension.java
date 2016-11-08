package io.vertx.groovy.circuitbreaker;
public class GroovyExtension {
  public static <T>io.vertx.core.Future<java.lang.Object> executeWithFallback(io.vertx.circuitbreaker.CircuitBreaker j_receiver, io.vertx.core.Handler<io.vertx.core.Future<java.lang.Object>> command, java.util.function.Function<java.lang.Throwable, java.lang.Object> fallback) {
    return io.vertx.lang.groovy.ConversionHelper.wrap(j_receiver.executeWithFallback(command != null ? event -> command.handle(io.vertx.lang.groovy.ConversionHelper.wrap(event)) : null,
      fallback != null ? new java.util.function.Function<java.lang.Throwable, java.lang.Object>() {
      public java.lang.Object apply(java.lang.Throwable t) {
        java.lang.Throwable o = io.vertx.lang.groovy.ConversionHelper.wrap(t);
        java.lang.Object p = fallback.apply(o);
        return io.vertx.lang.groovy.ConversionHelper.unwrap(p);
      }
    } : null));
  }
  public static <T>io.vertx.circuitbreaker.CircuitBreaker executeAndReportWithFallback(io.vertx.circuitbreaker.CircuitBreaker j_receiver, io.vertx.core.Future<java.lang.Object> resultFuture, io.vertx.core.Handler<io.vertx.core.Future<java.lang.Object>> command, java.util.function.Function<java.lang.Throwable, java.lang.Object> fallback) {
    io.vertx.lang.groovy.ConversionHelper.wrap(j_receiver.executeAndReportWithFallback(resultFuture,
      command != null ? event -> command.handle(io.vertx.lang.groovy.ConversionHelper.wrap(event)) : null,
      fallback != null ? new java.util.function.Function<java.lang.Throwable, java.lang.Object>() {
      public java.lang.Object apply(java.lang.Throwable t) {
        java.lang.Throwable o = io.vertx.lang.groovy.ConversionHelper.wrap(t);
        java.lang.Object p = fallback.apply(o);
        return io.vertx.lang.groovy.ConversionHelper.unwrap(p);
      }
    } : null));
    return j_receiver;
  }
  public static <T>io.vertx.circuitbreaker.CircuitBreaker fallback(io.vertx.circuitbreaker.CircuitBreaker j_receiver, java.util.function.Function<java.lang.Throwable, java.lang.Object> handler) {
    io.vertx.lang.groovy.ConversionHelper.wrap(j_receiver.fallback(handler != null ? new java.util.function.Function<java.lang.Throwable, java.lang.Object>() {
      public java.lang.Object apply(java.lang.Throwable t) {
        java.lang.Throwable o = io.vertx.lang.groovy.ConversionHelper.wrap(t);
        java.lang.Object p = handler.apply(o);
        return io.vertx.lang.groovy.ConversionHelper.unwrap(p);
      }
    } : null));
    return j_receiver;
  }
}
