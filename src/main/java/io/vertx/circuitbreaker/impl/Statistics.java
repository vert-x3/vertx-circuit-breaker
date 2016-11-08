package io.vertx.circuitbreaker.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.lang.Math.floor;

/**
 * A class computing a set of statistics on a set of numbers (long).
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Statistics {

  //TODO This may be a memory leak as we keep adding value to the list.

  private final List<Long> values = new ArrayList<>();

  /**
   * Create a new {@link Statistics} (empty).
   */
  Statistics() {
    // Do nothing.
  }

  /**
   * Adds this value to the set of numbers.
   *
   * @param val the value
   * @return the current {@link Statistics} instance
   */
  public Statistics add(long val) {
    values.add(val);
    return this;
  }

  /**
   * Returns the value at the given quantile.
   *
   * @param quantile a given quantile, in {@code [0..1]}
   * @return the value in the distribution at {@code quantile}
   */
  long getValue(double quantile) {
    Collections.sort(this.values);
    if (quantile < 0.0 || quantile > 1.0 || Double.isNaN(quantile)) {
      throw new IllegalArgumentException(quantile + " is not in [0..1]");
    }

    if (values.size() == 0) {
      return 0;
    }

    final double pos = quantile * (values.size() + 1);
    final int index = (int) pos;

    if (index < 1) {
      return Math.round(values.get(0));
    }

    if (index >= values.size()) {
      return Math.round(values.get(values.size() - 1));
    }

    final double lower = values.get(index - 1);
    final double upper = values.get(index);
    return Math.round(lower + (pos - floor(pos)) * (upper - lower));
  }

  /**
   * Returns the arithmetic mean of the values in the snapshot.
   *
   * @return the arithmetic mean
   */
  double getMean() {
    if (values.size() == 0) {
      return 0;
    }

    double sum = 0;
    for (long value : values) {
      sum += value;
    }
    return sum / values.size();
  }

  /**
   * Returns the median value in the distribution.
   *
   * @return the median value
   */
  long getMedian() {
    return getValue(0.5);
  }

  /**
   * Returns the value at the 75th percentile in the distribution.
   *
   * @return the value at the 75th percentile
   */
  long get75thPercentile() {
    return getValue(0.75);
  }

  /**
   * Returns the value at the 95th percentile in the distribution.
   *
   * @return the value at the 95th percentile
   */
  long get95thPercentile() {
    return getValue(0.95);
  }

  /**
   * Returns the value at the 99th percentile in the distribution.
   *
   * @return the value at the 99th percentile
   */
  long get99thPercentile() {
    return getValue(0.99);
  }


}
