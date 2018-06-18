package io.vertx.circuitbreaker;

/**
 * Exception reported when the monitored operation timed out.
 * <p>
 * For performance reason, this exception does not carry a stack trace.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class TimeoutException extends RuntimeException {

  public TimeoutException() {
    super("operation timeout", null, false, false);
  }

}
