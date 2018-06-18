package io.vertx.circuitbreaker;

/**
 * Exception reported when the circuit breaker is open.
 * <p>
 * For performance reason, this exception does not carry a stack trace. You are not allowed to set a stack trace or a
 * cause to this exception. This <em>immutability</em> allows using a singleton instance.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class OpenCircuitException extends RuntimeException {

  public static OpenCircuitException INSTANCE = new OpenCircuitException();

  private OpenCircuitException() {
    super("open circuit", null, false, false);
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
