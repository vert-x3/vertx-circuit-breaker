# Metrics demo


These files are a simple demo to illustrate the Hystrix Dashboard.

1. Start the Hystrix Dashboard (we recommend using https://github.com/kennedyoliveira/standalone-hystrix-dashboard)
2. Start the DashboardExample creating 3 different circuit breakers
3. On the dashboard, register the metrics endpoint: http://localhost:8080/metrics
4. Start the RandomClient generating load
5. The circuit breaker metrics are now published in the dashboard

