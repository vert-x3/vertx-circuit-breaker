/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.circuitbreaker;

import io.vertx.codegen.annotations.VertxGen;

import java.util.concurrent.ThreadLocalRandom;

import static java.lang.Math.*;

/**
 * A policy for retry execution.
 */
@VertxGen
@FunctionalInterface
public interface RetryPolicy {

  /**
   * Create a constant delay retry policy.
   *
   * @param delay the constant delay in milliseconds
   */
  static RetryPolicy constantDelay(long delay) {
    if (delay <= 0) {
      throw new IllegalArgumentException("delay must be strictly positive");
    }
    return (failure, retryCount) -> delay;
  }

  /**
   * Create a linear delay retry policy.
   *
   * @param initialDelay the initial delay in milliseconds
   * @param maxDelay     maximum delay in milliseconds
   */
  static RetryPolicy linearDelay(long initialDelay, long maxDelay) {
    if (initialDelay <= 0) {
      throw new IllegalArgumentException("initialDelay must be strictly positive");
    }
    if (maxDelay < initialDelay) {
      throw new IllegalArgumentException("maxDelay must be greater than initialDelay");
    }
    return (failure, retryCount) -> min(maxDelay, initialDelay * retryCount);
  }

  /**
   * Create an exponential delay with jitter retry policy.
   * <p>
   * Based on the <em>Full Jitter</em> approach described in
   * <a href="https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/">Exponential Backoff And Jitter</a>.
   *
   * @param initialDelay the initial delay in milliseconds
   * @param maxDelay     maximum delay in milliseconds
   */
  static RetryPolicy exponentialDelayWithJitter(long initialDelay, long maxDelay) {
    if (initialDelay <= 0) {
      throw new IllegalArgumentException("initialDelay must be strictly positive");
    }
    if (maxDelay < initialDelay) {
      throw new IllegalArgumentException("maxDelay must be greater than initialDelay");
    }
    return (failure, retryCount) -> {
      ThreadLocalRandom random = ThreadLocalRandom.current();
      long delay = initialDelay * (1L << retryCount);
      return random.nextLong(0, delay < 0 ? maxDelay : min(maxDelay, delay));
    };
  }

  /**
   * Compute a delay in milliseconds before retry is executed.
   *
   * @param failure    the failure of the previous execution attempt
   * @param retryCount the number of times operation has been retried already
   * @return a delay in milliseconds before retry is executed
   */
  long delay(Throwable failure, int retryCount);

}
