package io.vertx.circuitbreaker;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.circuitbreaker.CircuitBreakerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.circuitbreaker.CircuitBreakerOptions} original class using Vert.x codegen.
 */
public class CircuitBreakerOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, CircuitBreakerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "maxFailures":
          if (member.getValue() instanceof Number) {
            obj.setMaxFailures(((Number)member.getValue()).intValue());
          }
          break;
        case "timeout":
          if (member.getValue() instanceof Number) {
            obj.setTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "fallbackOnFailure":
          if (member.getValue() instanceof Boolean) {
            obj.setFallbackOnFailure((Boolean)member.getValue());
          }
          break;
        case "resetTimeout":
          if (member.getValue() instanceof Number) {
            obj.setResetTimeout(((Number)member.getValue()).longValue());
          }
          break;
        case "notificationLocalOnly":
          if (member.getValue() instanceof Boolean) {
            obj.setNotificationLocalOnly((Boolean)member.getValue());
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
        case "metricsRollingWindow":
          if (member.getValue() instanceof Number) {
            obj.setMetricsRollingWindow(((Number)member.getValue()).longValue());
          }
          break;
        case "failuresRollingWindow":
          if (member.getValue() instanceof Number) {
            obj.setFailuresRollingWindow(((Number)member.getValue()).longValue());
          }
          break;
        case "metricsRollingBuckets":
          if (member.getValue() instanceof Number) {
            obj.setMetricsRollingBuckets(((Number)member.getValue()).intValue());
          }
          break;
        case "maxRetries":
          if (member.getValue() instanceof Number) {
            obj.setMaxRetries(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

   static void toJson(CircuitBreakerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(CircuitBreakerOptions obj, java.util.Map<String, Object> json) {
    json.put("maxFailures", obj.getMaxFailures());
    json.put("timeout", obj.getTimeout());
    json.put("fallbackOnFailure", obj.isFallbackOnFailure());
    json.put("resetTimeout", obj.getResetTimeout());
    json.put("notificationLocalOnly", obj.isNotificationLocalOnly());
    if (obj.getNotificationAddress() != null) {
      json.put("notificationAddress", obj.getNotificationAddress());
    }
    json.put("notificationPeriod", obj.getNotificationPeriod());
    json.put("metricsRollingWindow", obj.getMetricsRollingWindow());
    json.put("failuresRollingWindow", obj.getFailuresRollingWindow());
    json.put("metricsRollingBuckets", obj.getMetricsRollingBuckets());
    json.put("maxRetries", obj.getMaxRetries());
  }
}
