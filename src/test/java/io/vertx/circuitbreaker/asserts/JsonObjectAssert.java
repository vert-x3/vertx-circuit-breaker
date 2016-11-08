package io.vertx.circuitbreaker.asserts;

import io.vertx.core.json.JsonObject;
import org.assertj.core.api.AbstractAssert;

/**
 * Assertions for Json Objects
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class JsonObjectAssert extends AbstractAssert<JsonObjectAssert, JsonObject> {
  public JsonObjectAssert(JsonObject actual) {
    super(actual, JsonObjectAssert.class);
  }

  public JsonObjectAssert contains(String field, String value) {
    if (actual == null) {
      failWithMessage("The given json object is null");
    } else {
      String contained = actual.getString(field);
      if (contained == null || !contained.equals(value)) {
        failWithMessage("The given json object does not contain `" + field + "`=`" + value
          + "`: " + actual.encodePrettily());
      }
    }
    return this;
  }

  public JsonObjectAssert contains(String field, long value) {
    if (actual == null) {
      failWithMessage("The given json object is null");
    } else {
      Long contained = actual.getLong(field);
      if (contained == null || !contained.equals(value)) {
        failWithMessage("The given json object does not contain `" + field + "`=`" + value
          + "`: " + actual.encodePrettily());
      }
    }
    return this;
  }

  public JsonObjectAssert contains(String field, double value) {
    if (actual == null) {
      failWithMessage("The given json object is null");
    } else {
      Double contained = actual.getDouble(field);
      if (contained == null || !contained.equals(value)) {
        failWithMessage("The given json object does not contain `" + field + "`=`" + value
          + "`: " + actual.encodePrettily());
      }
    }
    return this;
  }
}
