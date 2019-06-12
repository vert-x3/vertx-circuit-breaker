/*
 * Copyright (c) 2011-2016 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.circuitbreaker.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * Some methods using asynchronous patterns.
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class MyAsyncOperations {

  public static void operation(int a, int b, Handler<AsyncResult<Integer>> handler) {
    handler.handle(Future.succeededFuture(a + b));
  }

  public static void fail(Handler<AsyncResult<Integer>> handler) {
    handler.handle(Future.failedFuture("boom"));
  }

  public static void operation(Promise<Integer> future, int a, int b) {
    future.complete(a + b);
  }

  public static void fail(Promise<Integer> future) {
    future.fail("boom");
  }


}
