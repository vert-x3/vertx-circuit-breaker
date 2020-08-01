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

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class HttpClientCommand extends HystrixCommand<String> {

  private final HttpClient client;
  private final String path;

  public HttpClientCommand(HttpClient client, String path) {
    super(HystrixCommandGroupKey.Factory.asKey("test"));
    this.client = client;
    this.path = path;
  }

  @Override
  protected String run() throws Exception {
    AtomicReference<String> result = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    Handler<Throwable> errorHandler = t -> {
      latch.countDown();
    };

    client.request(HttpMethod.GET, path, ar1 -> {
      if (ar1.succeeded()) {
        HttpClientRequest req = ar1.result();
        req.send(ar2 -> {
          if (ar2.succeeded()) {
            HttpClientResponse response = ar2.result();
            response.exceptionHandler(errorHandler);
            if (response.statusCode() != 200) {
              latch.countDown();
              return;
            }
            response.bodyHandler(content -> {
              result.set(content.toString());
              latch.countDown();
            });
          } else {
            errorHandler.handle(ar2.cause());
          }
        });
      } else {
        errorHandler.handle(ar1.cause());
      }
    });

    latch.await();

    if (result.get() == null) {
      throw new RuntimeException("Failed to retrieve the HTTP response");
    } else {
      return result.get();
    }
  }

  @Override
  protected String getFallback() {
    return "fallback";
  }
}
