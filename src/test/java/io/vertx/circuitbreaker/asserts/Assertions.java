package io.vertx.circuitbreaker.asserts;

import io.vertx.core.AsyncResult;
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Assertions {

  public static <T> AsyncResultAssert<T> assertThat(AsyncResult<T> ar) {
    return new AsyncResultAssert<>(ar);
  }

  public static JsonObjectAssert assertThat(JsonObject json) {
    return new JsonObjectAssert(json);
  }
}
