package io.vertx.circuitbreaker.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static java.lang.Math.floor;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Statistics {

  private final List<Long> values = new ArrayList<>();

  /**
   * Create a new {@link Statistics} (empty).
   */
  public Statistics() {

  }

  /**
   * Create a new {@link Statistics} with the given values.
   *
   * @param values an unordered set of values in the reservoir
   */
  public Statistics(Collection<Long> values) {
    this.values.addAll(values);
    Collections.sort(this.values);
  }

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
  public long getValue(double quantile) {
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
   * Returns the number of values in the snapshot.
   *
   * @return the number of values
   */
  public int size() {
    return values.size();
  }

  /**
   * Returns the entire set of values in the snapshot.
   *
   * @return the entire set of values
   */
  public List<Long> getValues() {
    return new ArrayList<>(values);
  }

  /**
   * Returns the arithmetic mean of the values in the snapshot.
   *
   * @return the arithmetic mean
   */
  public double getMean() {
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
  public long getMedian() {
    return getValue(0.5);
  }

  /**
   * Returns the value at the 75th percentile in the distribution.
   *
   * @return the value at the 75th percentile
   */
  public long get75thPercentile() {
    return getValue(0.75);
  }

  /**
   * Returns the value at the 95th percentile in the distribution.
   *
   * @return the value at the 95th percentile
   */
  public long get95thPercentile() {
    return getValue(0.95);
  }

  /**
   * Returns the value at the 99th percentile in the distribution.
   *
   * @return the value at the 99th percentile
   */
  public long get99thPercentile() {
    return getValue(0.99);
  }


}
