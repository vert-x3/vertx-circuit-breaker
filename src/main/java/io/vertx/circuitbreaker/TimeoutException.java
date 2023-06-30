package io.vertx.circuitbreaker;

/**
 * Exception reported when the monitored operation timed out.
 * <p>
 * For performance reason, this exception does not carry a stack trace.  You are not allowed to set a stack trace or a
 * cause to this exception. This <em>immutability</em> allows using a singleton instance.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class TimeoutException extends RuntimeException {

  public static TimeoutException INSTANCE = new TimeoutException();

  private TimeoutException() {
    super("operation timeout", null, false, false);
  }

  @Override
  public void setStackTrace(StackTraceElement[] stackTrace) {
    throw new UnsupportedOperationException();
  }

  @Override
  public synchronized Throwable initCause(Throwable cause) {
    throw new UnsupportedOperationException();
  }

}
