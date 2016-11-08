package io.vertx.circuitbreaker.impl;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class StatisticsTest {

  @Test
  public void testOnEmptyStats() {
    Statistics statistics = new Statistics();
    assertThat(statistics.getMean()).isEqualTo(0);
    assertThat(statistics.get75thPercentile()).isEqualTo(0);
    assertThat(statistics.get95thPercentile()).isEqualTo(0);
    assertThat(statistics.get99thPercentile()).isEqualTo(0);
    assertThat(statistics.getMedian()).isEqualTo(0);
  }

  @Test
  public void test() {
    Statistics statistics = new Statistics()
      .add(1)
      .add(2)
      .add(3)
      .add(4);
    assertThat(statistics.getMean()).isEqualTo(2.5);
    assertThat(statistics.get75thPercentile()).isEqualTo(4);
    assertThat(statistics.get95thPercentile()).isEqualTo(4);
    assertThat(statistics.get99thPercentile()).isEqualTo(4);
    assertThat(statistics.getMedian()).isEqualTo(3);
  }

}
