require 'vertx/vertx'
require 'vertx-web/routing_context'
require 'vertx/util/utils.rb'
# Generated from io.vertx.circuitbreaker.HystrixMetricHandler
module VertxCircuitBreaker
  class HystrixMetricHandler
    # @private
    # @param j_del [::VertxCircuitBreaker::HystrixMetricHandler] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxCircuitBreaker::HystrixMetricHandler] the underlying java delegate
    def j_del
      @j_del
    end
    # @param [::VertxWeb::RoutingContext] arg0 
    # @return [void]
    def handle(arg0=nil)
      if arg0.class.method_defined?(:j_del) && !block_given?
        return @j_del.java_method(:handle, [Java::IoVertxExtWeb::RoutingContext.java_class]).call(arg0.j_del)
      end
      raise ArgumentError, "Invalid arguments when calling handle(arg0)"
    end
    # @param [::Vertx::Vertx] vertx 
    # @param [Hash] options 
    # @return [::VertxCircuitBreaker::HystrixMetricHandler]
    def self.create(vertx=nil,options=nil)
      if vertx.class.method_defined?(:j_del) && options.class == Hash && !block_given?
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxCircuitbreaker::HystrixMetricHandler.java_method(:create, [Java::IoVertxCore::Vertx.java_class,Java::IoVertxCircuitbreaker::CircuitBreakerOptions.java_class]).call(vertx.j_del,Java::IoVertxCircuitbreaker::CircuitBreakerOptions.new(::Vertx::Util::Utils.to_json_object(options))),::VertxCircuitBreaker::HystrixMetricHandler)
      end
      raise ArgumentError, "Invalid arguments when calling create(vertx,options)"
    end
  end
end
