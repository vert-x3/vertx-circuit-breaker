package io.vertx.circuitbreaker.asserts;

import io.vertx.core.AsyncResult;
import org.assertj.core.api.AbstractAssert;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class AsyncResultAssert<T> extends AbstractAssert<AsyncResultAssert<T>, AsyncResult<T>> {
  public AsyncResultAssert(AsyncResult<T> actual) {
    super(actual, AsyncResultAssert.class);
  }

  public AsyncResultAssert<T> succeeded() {
    if (!actual.succeeded()) {
      failWithMessage("AsyncResult has failed with " + actual.cause().getMessage());
    }
    return this;
  }

  public AsyncResultAssert<T> failed() {
    if (actual.succeeded()) {
      failWithMessage("AsyncResult has succeeded with " + actual.result());
    }
    return this;
  }
}
