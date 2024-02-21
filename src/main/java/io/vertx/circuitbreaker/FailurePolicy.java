package io.vertx.circuitbreaker;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;

import java.util.function.Predicate;

/**
 * A failure policy for the {@link CircuitBreaker}.
 * <p>
 * The default policy is to consider an asynchronous result as a failure if {@link AsyncResult#failed()} returns {@code true}.
 * Nevertheless, sometimes this is not good enough. For example, an HTTP Client could return a response, but with an unexpected status code.
 * <p>
 * In this case, a custom failure policy can be configured with {@link CircuitBreaker#failurePolicy(FailurePolicy)}.
 */
@VertxGen
public interface FailurePolicy<T> extends Predicate<Future<T>> {

  /**
   * The default policy, which considers an asynchronous result as a failure if {@link AsyncResult#failed()} returns {@code true}.
   */
  static <U> FailurePolicy<U> defaultPolicy() {
    return AsyncResult::failed;
  }

  /**
   * Invoked by the {@link CircuitBreaker} when an operation completes.
   *
   * @param future a completed future
   * @return {@code true} if the asynchronous result should be considered as a failure, {@code false} otherwise
   */
  @Override
  boolean test(Future<T> future);
}
