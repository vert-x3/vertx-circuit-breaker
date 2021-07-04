package io.vertx.circuitbreaker;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static io.vertx.circuitbreaker.MathUtil.*;

/**
 * Determines how long to wait before retrying a failed execution.
 * <p>Retries can be cancelled by returning a value of {@code -1L}.</p>
 *
 * <hr>
 *
 * <p>Includes default implementations of common retry strategies
 * based on strategies from the AWS Architecture Blog post "Exponential Backoff And Jitter",
 * and implementations from michaelbull/kotlin-retry.</p>
 *
 * @see <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">Exponential Backoff And Jitter</a>
 * @see <a href="https://github.com/michaelbull/kotlin-retry">michaelbull/kotlin-retry</a>
 */
@VertxGen
@FunctionalInterface
public interface RetryPolicy {

  /**
   * @return the delay (in milliseconds) to wait before retrying, or {@code -1L} to stop retrying.
   */
  long apply(int attempt, @Nullable Throwable failure);

  /**
   * Same as {@link #exponentialBackoff(long, Predicate)}, but cannot be cancelled.
   */
  static RetryPolicy exponentialBackoff(long max) {
    return exponentialBackoff(max, null);
  }

  /**
   * Creates a {@link RetryPolicy} that retries executions exponentially,
   * increasing the delay from 1000ms up to the {@code max}.
   *
   * <p><bold>Pseudocode:</bold></p>
   * <pre><code>delay = min(max, 1000 * 2 ** retryCount)</code></pre>
   *
   * @param max         the maximum possible delay (ms)
   * @param shouldRetry whether the execution should be retried
   * @see <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">Exponential Backoff And Jitter</a>
   */
  static RetryPolicy exponentialBackoff(long max, @Nullable Predicate<Throwable> shouldRetry) {
    if (max <= 0) throw new IllegalArgumentException("max must be positive: " + max);

    return (retryCount, failure) -> {
      if (shouldRetry != null && !shouldRetry.test(failure)) {
        return -1L;
      }

      ThreadLocalRandom random = ThreadLocalRandom.current();
      long jitter = random.nextLong(1, 1000);
      /* sleep = min(cap, base * 2 ** attempt) */
      return Math.min(max, saturatedMultiply(1000L, saturatedExponential(retryCount))) + jitter;
    };
  }

  /**
   * Same as {@link #fullJitterBackoff(long, Predicate)}, but cannot be cancelled.
   */
  static RetryPolicy fullJitterBackoff(long max) {
    return fullJitterBackoff(max, null);
  }

  /**
   * Creates a {@link RetryPolicy} that retries executions a random amount of ms
   * between 1000 and {@code max}, increasing the delay by 2 to the power of {@code retryCount}.
   *
   * <p><bold>Pseudocode:</bold></p>
   * <pre><code>delay = random_between(0, min(max, 1000 * 2 ** retryCount))</code></pre>
   *
   * @param max         the maximum possible delay (ms)
   * @param shouldRetry whether the execution should be retried
   * @see <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">Exponential Backoff And Jitter</a>
   */
  static RetryPolicy fullJitterBackoff(long max, @Nullable Predicate<Throwable> shouldRetry) {
    if (max <= 0) throw new IllegalArgumentException("max must be positive: " + max);

    return (retryCount, failure) -> {
      if (shouldRetry != null && !shouldRetry.test(failure)) {
        return -1L;
      }

      ThreadLocalRandom random = ThreadLocalRandom.current();
      /* sleep = random_between(0, min(cap, base * 2 ** attempt)) */
      long delay = Math.min(max, saturatedMultiply(1000L, saturatedExponential(retryCount)));
      return random.nextLong(saturatedAdd(delay, 1));
    };
  }

  /**
   * Same as {@link #equalJitterBackoff(long, Predicate)}, but cannot be cancelled.
   */
  static RetryPolicy equalJitterBackoff(long max) {
    return equalJitterBackoff(max, null);
  }

  /**
   * Creates a {@link RetryPolicy} that retries executions an amount
   * equally portioned between {@link #exponentialBackoff(long, Predicate)} and {@link #fullJitterBackoff(long, Predicate)}.
   *
   * <p><bold>Pseudocode:</bold></p>
   * <pre>
   *   <code>
   *     temp = min(max, 1000 * 2 ** shouldRetry)
   *     delay = temp / 2 + random_between(0, temp / 2)
   *   </code>
   * </pre>
   *
   * @param max         the maximum possible delay (ms)
   * @param shouldRetry whether the execution should be retried
   * @see <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">Exponential Backoff And Jitter</a>
   */
  static RetryPolicy equalJitterBackoff(long max, @Nullable Predicate<Throwable> shouldRetry) {
    if (max <= 0) throw new IllegalArgumentException("max must be positive: " + max);

    return (retryCount, throwable) -> {
      if (shouldRetry != null && !shouldRetry.test(throwable)) {
        return -1L;
      }

      ThreadLocalRandom random = ThreadLocalRandom.current();
      /* temp = min(cap, base * 2 ** attempt) */
      long delay = Math.min(max, saturatedMultiply(1000L, saturatedExponential(retryCount)));
      /* sleep = temp / 2 + random_between(0, temp / 2) */
      long half = (delay / 2);
      return saturatedAdd(half, random.nextLong(saturatedAdd(half, 1)));
    };
  }

  // Decorrelated Jitter is not possible to implement right now,
  // as it requires knowing the previous delay.
//  static RetryPolicy decorrelatedJitterBackoff(long max, @Nullable Predicate<Throwable> shouldRetry) {
//    throw new UnsupportedOperationException();
//  }
}
