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

package examples;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.RetryPolicy;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CircuitBreakerExamples {

  public void example1(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
        new CircuitBreakerOptions()
            .setMaxFailures(5) // number of failure before opening the circuit
            .setTimeout(2000) // consider a failure if the operation does not succeed in time
            .setFallbackOnFailure(true) // do we call the fallback on failure
            .setResetTimeout(10000) // time spent in open state before attempting to re-try
    );

    // ---
    // Store the circuit breaker in a field and access it as follows
    // ---

    breaker.execute(promise -> {
      // some code executing with the breaker
      // the code reports failures or success on the given promise.
      // if this promise is marked as failed, the breaker increased the
      // number of failures
    }).onComplete(ar -> {
      // Get the operation result.
    });
  }

  public void example2(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
        new CircuitBreakerOptions().setMaxFailures(5).setTimeout(2000)
    );

    // ---
    // Store the circuit breaker in a field and access it as follows
    // ---

    breaker.<String>execute(promise -> {
      vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
        .compose(req -> req
          .send()
          .compose(resp -> {
            if (resp.statusCode() != 200) {
              return Future.failedFuture("HTTP error");
            } else {
              return resp.body().map(Buffer::toString);
            }
          })).onComplete(promise);
    }).onComplete(ar -> {
      // Do something with the result
    });
  }

  public void example3(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
        new CircuitBreakerOptions().setMaxFailures(5).setTimeout(2000)
    );

    // ---
    // Store the circuit breaker in a field and access it as follows
    // ---

    breaker.executeWithFallback(
        promise -> {
          vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
            .compose(req -> req
              .send()
              .compose(resp -> {
                if (resp.statusCode() != 200) {
                  return Future.failedFuture("HTTP error");
                } else {
                  return resp.body().map(Buffer::toString);
                }
              })).onComplete(promise);
          }, v -> {
          // Executed when the circuit is opened
          return "Hello";
        })
        .onComplete(ar -> {
          // Do something with the result
        });
  }

  public void example4(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
        new CircuitBreakerOptions().setMaxFailures(5).setTimeout(2000)
    ).fallback(v -> {
      // Executed when the circuit is opened.
      return "hello";
    });

    breaker.<String>execute(
        promise -> {
          vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
            .compose(req -> req
              .send()
              .compose(resp -> {
                if (resp.statusCode() != 200) {
                  return Future.failedFuture("HTTP error");
                } else {
                  return resp.body().map(Buffer::toString);
                }
              })).onComplete(promise);
        });
  }

  public void example5(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
        new CircuitBreakerOptions().setMaxFailures(5).setTimeout(2000)
    ).openHandler(v -> {
      System.out.println("Circuit opened");
    }).closeHandler(v -> {
      System.out.println("Circuit closed");
    });

    breaker.<String>execute(
        promise -> {
          vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
            .compose(req -> req
              .send()
              .compose(resp -> {
                if (resp.statusCode() != 200) {
                  return Future.failedFuture("HTTP error");
                } else {
                  return resp.body().map(Buffer::toString);
                }
              })).onComplete(promise);
        });
  }

  public void example6(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
        new CircuitBreakerOptions().setMaxFailures(5).setTimeout(2000)
    );

    Promise<String> userPromise = Promise.promise();
    userPromise.future().onComplete(ar -> {
      // Do something with the result
    });

    breaker.executeAndReportWithFallback(
        userPromise,
        promise -> {
          vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
            .compose(req -> req
              .send()
              .compose(resp -> {
                if (resp.statusCode() != 200) {
                  return Future.failedFuture("HTTP error");
                } else {
                  return resp.body().map(Buffer::toString);
                }
              })).onComplete(promise);
        }, v -> {
          // Executed when the circuit is opened
          return "Hello";
        });
  }

  public void example8(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
      new CircuitBreakerOptions().setMaxFailures(5).setMaxRetries(5).setTimeout(2000)
    ).openHandler(v -> {
      System.out.println("Circuit opened");
    }).closeHandler(v -> {
      System.out.println("Circuit closed");
    }).retryPolicy(RetryPolicy.exponentialDelayWithJitter(50, 500));

    breaker.<String>execute(
      promise -> {
        vertx.createHttpClient().request(HttpMethod.GET, 8080, "localhost", "/")
          .compose(req -> req
            .send()
            .compose(resp -> {
              if (resp.statusCode() != 200) {
                return Future.failedFuture("HTTP error");
              } else {
                return resp.body().map(Buffer::toString);
              }
            })).onComplete(promise);
      });
  }

  public void enableNotifications(CircuitBreakerOptions options) {
    options.setNotificationAddress(CircuitBreakerOptions.DEFAULT_NOTIFICATION_ADDRESS);
  }
}
