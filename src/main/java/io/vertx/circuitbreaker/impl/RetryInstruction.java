package io.vertx.circuitbreaker.impl;


/**
 * Represents whether the execution should continue retrying.
 */
enum RetryInstruction {
  CONTINUE, STOP;

  /**
   * Returns a {@link RetryInstruction} based on the provided delay.
   * <p>A delay of {@code -1L} means "stop retrying".</p>
   *
   * @return {@link RetryInstruction#STOP} if the delay equals "-1L", otherwise {@link RetryInstruction#CONTINUE}
   */
  static RetryInstruction of(long retryDelay) {
    return (retryDelay == -1L) ? RetryInstruction.STOP : RetryInstruction.CONTINUE;
  }
}
