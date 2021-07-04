package io.vertx.circuitbreaker;

/**
 * Utility methods for math operations likely to overflow.
 */
class MathUtil {
  private MathUtil() {
  }

  /**
   * Returns 2 to the power of {@code a}, unless it would overflow, in which case
   * {@link Long#MAX_VALUE} is returned instead.
   *
   * @see <a href="https://github.com/michaelbull/kotlin-retry/blob/fdeecec782916dc9daec0caa76937b521f8ed28f/src/main/kotlin/com/github/michaelbull/retry/Math.kt#L31-L41>Original implementation</a>
   */
  static long saturatedExponential(int a) {
    if (a < (Long.BYTES * 8) - 1) {
      return 1L << a;
    } else {
      return Long.MAX_VALUE;
    }
  }

  /**
   * Returns the sum of {@code a} and {@code b} unless it would overflow or underflow in which case
   * {@code Long.MAX_VALUE} or {@code Long.MIN_VALUE} is returned, respectively.
   *
   * @see <a href="https://guava.dev/releases/20.0/api/docs/com/google/common/math/LongMath.html#saturatedMultiply-long-long-">Original implementation</a>
   */
  public static long saturatedAdd(long a, long b) {
    long naiveSum = a + b;
    if ((a ^ b) < 0 | (a ^ naiveSum) >= 0) {
      // If a and b have different signs or a has the same sign as the result then there was no
      // overflow, return.
      return naiveSum;
    }
    // we did over/under flow, if the sign is negative we should return MAX otherwise MIN
    return Long.MAX_VALUE + ((naiveSum >>> (Long.SIZE - 1)) ^ 1);
  }

  /**
   * Returns the product of {@code a} and {@code b} unless it would overflow or underflow in which
   * case {@code Long.MAX_VALUE} or {@code Long.MIN_VALUE} is returned, respectively.
   *
   * @see <a href="https://guava.dev/releases/20.0/api/docs/com/google/common/math/LongMath.html#saturatedMultiply-long-long-">Original implementation</a>
   */
  static long saturatedMultiply(long a, long b) {
    // see checkedMultiply for explanation
    int leadingZeros =
      Long.numberOfLeadingZeros(a)
        + Long.numberOfLeadingZeros(~a)
        + Long.numberOfLeadingZeros(b)
        + Long.numberOfLeadingZeros(~b);
    if (leadingZeros > Long.SIZE + 1) {
      return a * b;
    }
    // the return value if we will overflow (which we calculate by overflowing a long :) )
    long limit = Long.MAX_VALUE + ((a ^ b) >>> (Long.SIZE - 1));
    if (leadingZeros < Long.SIZE | (a < 0 & b == Long.MIN_VALUE)) {
      // overflow
      return limit;
    }
    long result = a * b;
    if (a == 0 || result / a == b) {
      return result;
    }
    return limit;
  }
}
