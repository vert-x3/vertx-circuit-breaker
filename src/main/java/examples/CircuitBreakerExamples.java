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

import io.vertx.circuitbreaker.HystrixMetricHandler;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.ext.web.Router;

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

    breaker.execute(future -> {
      // some code executing with the breaker
      // the code reports failures or success on the given future.
      // if this future is marked as failed, the breaker increased the
      // number of failures
    }).setHandler(ar -> {
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

    breaker.<String>execute(future -> {
      vertx.createHttpClient().get(8080, "localhost", "/", ar -> {
        if (ar.succeeded()) {
          HttpClientResponse response = ar.result();
          if (response.statusCode() != 200) {
            future.fail("HTTP error");
          } else {
            response
              .exceptionHandler(future::fail)
              .bodyHandler(buffer -> {
                future.complete(buffer.toString());
              });
          }
        } else {
          future.fail("Request error");
        }
      });
    }).setHandler(ar -> {
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
        future -> {
          vertx.createHttpClient().get(8080, "localhost", "/", ar -> {
            if (ar.succeeded()) {
              HttpClientResponse response = ar.result();
              if (response.statusCode() != 200) {
                future.fail("HTTP error");
              } else {
                response
                  .exceptionHandler(future::fail)
                  .bodyHandler(buffer -> {
                    future.complete(buffer.toString());
                  });
              }
            } else {
              future.fail("Connect error");
            }
          });
        }, v -> {
          // Executed when the circuit is opened
          return "Hello";
        })
        .setHandler(ar -> {
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

    breaker.execute(
        future -> {
          vertx.createHttpClient().get(8080, "localhost", "/", ar -> {
            if (ar.succeeded()) {
              HttpClientResponse response = ar.result();
              if (response.statusCode() != 200) {
                future.fail("HTTP error");
              } else {
                response
                  .exceptionHandler(future::fail)
                  .bodyHandler(buffer -> {
                    future.complete(buffer.toString());
                  });
              }
            } else {
              future.fail("Connect error");
            }
          });
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

    breaker.execute(
        future -> {
          vertx.createHttpClient().get(8080, "localhost", "/", ar -> {
            if (ar.succeeded()) {
              HttpClientResponse response = ar.result();
              if (response.statusCode() != 200) {
                future.fail("HTTP error");
              } else {
                // Do something with the response
                future.complete();
              }
            } else {
              future.fail("Connect error");
            }
          });
        });
  }

  public void example6(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
        new CircuitBreakerOptions().setMaxFailures(5).setTimeout(2000)
    );

    Promise<String> userFuture = Promise.promise();
    userFuture.future().setHandler(ar -> {
      // Do something with the result
    });

    breaker.executeAndReportWithFallback(
        userFuture,
        future -> {
          vertx.createHttpClient().get(8080, "localhost", "/", ar -> {
            if (ar.succeeded()) {
              HttpClientResponse response = ar.result();
              if (response.statusCode() != 200) {
                future.fail("HTTP error");
              } else {
                response
                  .exceptionHandler(future::fail)
                  .bodyHandler(buffer -> {
                    future.complete(buffer.toString());
                  });
              }
            } else {
              future.fail("Connect error");
            }
          });
        }, v -> {
          // Executed when the circuit is opened
          return "Hello";
        });
  }

  public void example7(Vertx vertx) {
    // Create the circuit breaker as usual.
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx);
    CircuitBreaker breaker2 = CircuitBreaker.create("my-second-circuit-breaker", vertx);

    // Create a Vert.x Web router
    Router router = Router.router(vertx);
    // Register the metric handler
    router.get("/hystrix-metrics").handler(HystrixMetricHandler.create(vertx));

    // Create the HTTP server using the router to dispatch the requests
    vertx.createHttpServer()
      .requestHandler(router)
      .listen(8080);

  }

  public void example8(Vertx vertx) {
    CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
      new CircuitBreakerOptions().setMaxFailures(5).setMaxRetries(5).setTimeout(2000)
    ).openHandler(v -> {
      System.out.println("Circuit opened");
    }).closeHandler(v -> {
      System.out.println("Circuit closed");
    }).retryPolicy(retryCount -> retryCount * 100L);

    breaker.execute(
      future -> {
        vertx.createHttpClient().get(8080, "localhost", "/", ar -> {
          if (ar.succeeded()) {
            HttpClientResponse response = ar.result();
            if (response.statusCode() != 200) {
              future.fail("HTTP error");
            } else {
              // Do something with the response
              future.complete();
            }
          } else {
            future.fail("Connect error");
          }
        });
      });
  }
}
