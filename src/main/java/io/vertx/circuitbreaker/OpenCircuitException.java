package io.vertx.circuitbreaker;

/**
 * Exception reported when the circuit breaker is open.
 * <p>
 * For performance reason, this exception does not carry a stack trace.
 *
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class OpenCircuitException extends RuntimeException {

  public OpenCircuitException() {
    super("open circuit", null, false, false);
  }

}
