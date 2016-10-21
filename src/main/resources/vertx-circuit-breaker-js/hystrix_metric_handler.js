/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/** @module vertx-circuit-breaker-js/hystrix_metric_handler */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');
var RoutingContext = require('vertx-web-js/routing_context');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JHystrixMetricHandler = io.vertx.circuitbreaker.HystrixMetricHandler;
var CircuitBreakerOptions = io.vertx.circuitbreaker.CircuitBreakerOptions;

/**

 @class
*/
var HystrixMetricHandler = function(j_val) {

  var j_hystrixMetricHandler = j_val;
  var that = this;

  /**

   @public
   @param arg0 {RoutingContext} 
   */
  this.handle = function(arg0) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'object' && __args[0]._jdel) {
      j_hystrixMetricHandler["handle(io.vertx.ext.web.RoutingContext)"](arg0._jdel);
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_hystrixMetricHandler;
};

/**

 @memberof module:vertx-circuit-breaker-js/hystrix_metric_handler
 @param vertx {Vertx} 
 @param options {Object} 
 @return {HystrixMetricHandler}
 */
HystrixMetricHandler.create = function(vertx, options) {
  var __args = arguments;
  if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(JHystrixMetricHandler["create(io.vertx.core.Vertx,io.vertx.circuitbreaker.CircuitBreakerOptions)"](vertx._jdel, options != null ? new CircuitBreakerOptions(new JsonObject(JSON.stringify(options))) : null), HystrixMetricHandler);
  } else throw new TypeError('function invoked with invalid arguments');
};

// We export the Constructor function
module.exports = HystrixMetricHandler;