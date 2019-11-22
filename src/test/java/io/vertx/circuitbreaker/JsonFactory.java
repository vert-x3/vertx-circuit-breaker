package io.vertx.circuitbreaker;

import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.core.spi.json.JsonCodec;

public class JsonFactory implements io.vertx.core.spi.JsonFactory {

  @Override
  public JsonCodec codec() {
    return new JacksonCodec();
  }
}
