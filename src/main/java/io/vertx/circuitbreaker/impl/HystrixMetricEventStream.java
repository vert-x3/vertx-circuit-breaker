package io.vertx.circuitbreaker.impl;

import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.circuitbreaker.HystrixMetricHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * Implements a handler to serve the Vert.x circuit breaker metrics as a Hystrix circuit
 * breaker.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HystrixMetricEventStream implements HystrixMetricHandler {

  private final List<HttpServerResponse> connections = Collections.synchronizedList(new LinkedList<>());
  private AtomicInteger counter = new AtomicInteger();

  public HystrixMetricEventStream(Vertx vertx, String address) {
    Objects.requireNonNull(vertx);
    Objects.requireNonNull(address);

    vertx.eventBus().<JsonObject>consumer(address)
      .handler(message -> {
        JsonObject json = build(message.body());
        int id = counter.incrementAndGet();
        connections.forEach(resp -> {
          String chunk = json.encode() + "\n\n";
          resp.write("id" + ": " + id + "\n");
          resp.write("data:" + chunk);
        });
      });
  }

  private JsonObject build(JsonObject body) {
    String state = body.getString("state");
    JsonObject json = new JsonObject();
    json.put("type", "HystrixCommand");
    json.put("name", body.getString("name"));
    json.put("group", body.getString("node"));
    json.put("currentTime", System.currentTimeMillis());
    json.put("isCircuitBreakerOpen", state.equalsIgnoreCase(CircuitBreakerState.OPEN.toString()));
    json.put("errorPercentage", body.getInteger("totalErrorPercentage", 0));
    json.put("errorCount", body.getInteger("totalErrorCount", 0));
    json.put("requestCount", body.getInteger("rollingOperationCount", 0));
    json.put("rollingCountCollapsedRequests", 0); // TODO
    json.put("rollingCountExceptionsThrown", body.getInteger("rollingExceptionCount", 0));
    json.put("rollingCountFailure", body.getInteger("rollingFailureCount", 0));
    json.put("rollingCountTimeout", body.getInteger("rollingTimeoutCount", 0));
    json.put("rollingCountFallbackFailure", body.getInteger("rollingFallbackFailureCount", 0));
    json.put("rollingCountFallbackRejection", body.getInteger("fallbackRejection", 0));
    json.put("rollingCountFallbackSuccess", body.getInteger("rollingFallbackSuccessCount", 0));
    json.put("rollingCountResponsesFromCache", 0);
    json.put("rollingCountSemaphoreRejected", 0);
    json.put("rollingCountShortCircuited", body.getInteger("rollingShortCircuitedCount", 0));
    json.put("rollingCountSuccess", body.getInteger("rollingSuccessCount", 0));
    json.put("rollingCountThreadPoolRejected", 0);
    json.put("rollingCountTimeout", body.getInteger("rollingTimeoutCount", 0));
    json.put("currentConcurrentExecutionCount", 0);
    json.put("latencyExecute_mean", body.getInteger("rollingLatencyMean", 0));
    json.put("latencyExecute", body.getJsonObject("rollingLatency", new JsonObject()));

    json.put("propertyValue_circuitBreakerRequestVolumeThreshold", 0);
    json.put("propertyValue_circuitBreakerSleepWindowInMilliseconds", body.getLong("resetTimeout", 0L));
    json.put("propertyValue_circuitBreakerErrorThresholdPercentage", 0);
    json.put("propertyValue_circuitBreakerForceOpen", false);
    json.put("propertyValue_circuitBreakerForceClosed", false);
    json.put("propertyValue_circuitBreakerEnabled", true);
    json.put("propertyValue_executionIsolationStrategy", "THREAD");
    json.put("propertyValue_executionIsolationThreadTimeoutInMilliseconds",body.getLong("timeout", 0L));
    json.put("propertyValue_executionIsolationThreadInterruptOnTimeout", true);
    json.put("propertyValue_executionIsolationThreadPoolKeyOverride", "");
    json.put("propertyValue_executionIsolationSemaphoreMaxConcurrentRequests", 0);
    json.put("propertyValue_fallbackIsolationSemaphoreMaxConcurrentRequests", 0);
    json.put("propertyValue_metricsRollingStatisticalWindowInMilliseconds", body.getLong("metricRollingWindow", 0L));
    json.put("propertyValue_requestCacheEnabled", false);
    json.put("propertyValue_requestLogEnabled", false);
    json.put("reportingHosts", 1);
    return json;
  }

  @Override
  public void handle(RoutingContext rc) {
    HttpServerResponse response = rc.response();
    response
      .setChunked(true)
      .putHeader(HttpHeaders.CONTENT_TYPE, "text/event-stream")
      .putHeader(HttpHeaders.CACHE_CONTROL, "no-cache")
      .putHeader(HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE);

    rc.request().connection().closeHandler(v -> {
      connections.remove(response);
      response.end();
    });

    connections.add(response);
  }
}
