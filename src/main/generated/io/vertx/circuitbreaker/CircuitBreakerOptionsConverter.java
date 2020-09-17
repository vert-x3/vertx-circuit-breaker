package io.vertx.circuitbreaker;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.circuitbreaker.CircuitBreakerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.circuitbreaker.CircuitBreakerOptions} original class using Vert.x codegen.
 */
public class CircuitBreakerOptionsConverter {


  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, CircuitBreakerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "failuresRollingWindow":
          if (member.getValue() instanceof Number) {
            obj.setFailuresRollingWindow(((Number)member.getValue()).longValue());
          }
          break;
        case "fallbackOnFailure":
          if (member.getValue() instanceof Boolean) {
            obj.setFallbackOnFailure((Boolean)member.getValue());
          }
          break;
        case "maxFailures":
          if (member.getValue() instanceof Number) {
            obj.setMaxFailures(((Number)member.getValue()).intValue());
          }
          break;
        case "maxRetries":
          if (member.getValue() instanceof Number) {
            obj.setMaxRetries(((Number)member.getValue()).intValue());
          }
          break;
        case "metricsRollingBuckets":
          if (member.getValue() instanceof Number) {
            obj.setMetricsRollingBuckets(((Number)member.getValue()).intValue());
          }
          break;
        case "metricsRollingWindow":
          if (member.getValue() instanceof Number) {
            obj.setMetricsRollingWindow(((Number)member.getValue()).longValue());
          }
          break;
        case "notificationAddress":
          if (member.getValue() instanceof String) {
            obj.setNotificationAddress((String)member.getValue());
          }
          break;
        case "notificationPeriod":
          if (member.getValue() instanceof Number) {
            obj.setNotificationPeriod(((Number)member.getValue()).longValue());
          }
          break;
        case "resetTimeout":
          if (member.getValue() instanceof Number) {
            obj.setResetTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "timeout":
          if (member.getValue() instanceof Number) {
            obj.setTimeout(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

  public static void toJson(CircuitBreakerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(CircuitBreakerOptions obj, java.util.Map<String, Object> json) {
    json.put("failuresRollingWindow", obj.getFailuresRollingWindow());
    json.put("fallbackOnFailure", obj.isFallbackOnFailure());
    json.put("maxFailures", obj.getMaxFailures());
    json.put("maxRetries", obj.getMaxRetries());
    json.put("metricsRollingBuckets", obj.getMetricsRollingBuckets());
    json.put("metricsRollingWindow", obj.getMetricsRollingWindow());
    if (obj.getNotificationAddress() != null) {
      json.put("notificationAddress", obj.getNotificationAddress());
    }
    json.put("notificationPeriod", obj.getNotificationPeriod());
    json.put("resetTimeout", obj.getResetTimeout());
    json.put("timeout", obj.getTimeout());
  }
}
