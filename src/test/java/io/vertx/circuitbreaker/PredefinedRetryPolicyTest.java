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

import org.junit.Test;

import static org.junit.Assert.*;

public class PredefinedRetryPolicyTest {

  @Test(expected = IllegalArgumentException.class)
  public void testConstantDelayNegative() {
    RetryPolicy.constantDelay(-1);
  }

  @Test
  public void testConstantDelay() {
    RetryPolicy retryPolicy = RetryPolicy.constantDelay(10);
    for (int i = 1; i <= 50; i++) {
      assertEquals(10, retryPolicy.delay(null, i));
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testLinearDelayNegative() {
    RetryPolicy.linearDelay(-1, 30000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testLinearDelayGreatherThanMax() {
    RetryPolicy.linearDelay(50000, 30000);
  }

  @Test
  public void testLinearDelay() {
    RetryPolicy retryPolicy = RetryPolicy.linearDelay(10, 250);
    for (int i = 1; i <= 50; i++) {
      long delay = retryPolicy.delay(null, i);
      if (i <= 25) {
        assertEquals(10 * i, delay);
      } else {
        assertEquals(250, delay);
      }
    }
  }

  @Test(expected = IllegalArgumentException.class)
  public void testExponentialDelayNegative() {
    RetryPolicy.exponentialDelayWithJitter(-1, 30000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testExponentialDelayGreatherThanMax() {
    RetryPolicy.exponentialDelayWithJitter(50000, 30000);
  }

  @Test
  public void testExponentialDelayWithJitter() {
    int maxDelay = 30000;
    RetryPolicy retryPolicy = RetryPolicy.exponentialDelayWithJitter(3, maxDelay);
    for (int i = 1; i <= 50; i++) {
      long delay = retryPolicy.delay(null, i);
      assertTrue(0 <= delay && delay <= maxDelay);
    }
  }
}
