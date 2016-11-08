package io.vertx.circuitbreaker.metrics;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class RandomClient extends AbstractVerticle {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(RandomClient.class.getName(), new DeploymentOptions().setInstances(4));
  }

  List<String> paths = new ArrayList<>();
  Random random = new Random();

  @Override
  public void start() throws Exception {
    paths.add("/A");
    paths.add("/A");
    paths.add("/B");
    paths.add("/C");

    AtomicInteger counter = new AtomicInteger();
    vertx.setPeriodic(500, l -> {
      int index = random.nextInt(paths.size());
      int count = counter.getAndIncrement();
      vertx.createHttpClient().get(8080, "localhost", paths.get(index), response -> {
        System.out.println(this + "[" + count + "] (" + paths.get(index) + ") Response: " + response.statusMessage());
        response.bodyHandler(buffer -> {
          System.out.println(this + "[" + count + "] (" + paths.get(index) + ") Data: " + buffer.toString());
        });
      }).end();
    });
  }
}
