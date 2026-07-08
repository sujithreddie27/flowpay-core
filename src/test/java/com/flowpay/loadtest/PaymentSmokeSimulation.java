package com.flowpay.loadtest;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class PaymentSmokeSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final String AUTH_TOKEN = System.getProperty("authToken", "test-token");

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .header("Authorization", "Bearer " + AUTH_TOKEN);

    private final Iterator<Map<String, Object>> paymentFeeder = Stream.generate(
            (Supplier<Map<String, Object>>) () -> Map.of(
                    "senderAccountId", UUID.randomUUID().toString(),
                    "receiverAccountId", UUID.randomUUID().toString(),
                    "amount", String.valueOf(10 + Math.random() * 100),
                    "idempotencyKey", UUID.randomUUID().toString()
            )
    ).iterator();

    private final ScenarioBuilder smokeTest = scenario("Payment Smoke Test")
            .feed(paymentFeeder)
            .exec(
                    http("Health Check")
                            .get("/actuator/health")
                            .check(status().is(200))
            )
            .pause(Duration.ofMillis(100))
            .exec(
                    http("Initiate Payment")
                            .post("/api/v1/transactions")
                            .body(StringBody("""
                                    {
                                      "senderAccountId": "#{senderAccountId}",
                                      "receiverAccountId": "#{receiverAccountId}",
                                      "amount": #{amount},
                                      "currency": "USD",
                                      "type": "TRANSFER",
                                      "description": "Smoke test",
                                      "idempotencyKey": "#{idempotencyKey}"
                                    }
                                    """))
                            .check(status().in(201, 400, 404, 409))
            )
            .pause(Duration.ofMillis(50))
            .exec(
                    http("Transaction History")
                            .get("/api/v1/transactions/history")
                            .queryParam("page", "0")
                            .queryParam("size", "10")
                            .check(status().in(200, 403))
            );

    {
        setUp(
                smokeTest.injectOpen(
                        rampUsersPerSec(1).to(50).during(Duration.ofSeconds(10)),
                        constantUsersPerSec(50).during(Duration.ofSeconds(30)),
                        rampUsersPerSec(50).to(0).during(Duration.ofSeconds(5))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().mean().lt(1000),
                        global().successfulRequests().percent().gt(90.0)
                );
    }
}
