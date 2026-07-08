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

public class PaymentLoadSimulation extends Simulation {

    private static final String BASE_URL = System.getProperty("baseUrl", "http://localhost:8080");
    private static final String AUTH_TOKEN = System.getProperty("authToken", "test-token");

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .acceptHeader("application/json")
            .contentTypeHeader("application/json")
            .header("Authorization", "Bearer " + AUTH_TOKEN)
            .shareConnections();

    private final Iterator<Map<String, Object>> paymentFeeder = Stream.generate(
            (Supplier<Map<String, Object>>) () -> Map.of(
                    "senderAccountId", UUID.randomUUID().toString(),
                    "receiverAccountId", UUID.randomUUID().toString(),
                    "amount", String.valueOf(10 + Math.random() * 990),
                    "idempotencyKey", UUID.randomUUID().toString()
            )
    ).iterator();

    // Scenario 1: Single payment initiation
    private final ScenarioBuilder initiatePaymentScenario = scenario("Initiate Payment")
            .feed(paymentFeeder)
            .exec(
                    http("POST /api/v1/transactions")
                            .post("/api/v1/transactions")
                            .body(StringBody("""
                                    {
                                      "senderAccountId": "#{senderAccountId}",
                                      "receiverAccountId": "#{receiverAccountId}",
                                      "amount": #{amount},
                                      "currency": "USD",
                                      "type": "TRANSFER",
                                      "description": "Load test transfer",
                                      "idempotencyKey": "#{idempotencyKey}"
                                    }
                                    """))
                            .check(status().in(201, 400, 409, 404))
            );

    // Scenario 2: Get transaction by ID
    private final ScenarioBuilder getTransactionScenario = scenario("Get Transaction")
            .exec(
                    http("GET /api/v1/transactions/{id}")
                            .get("/api/v1/transactions/" + UUID.randomUUID())
                            .check(status().in(200, 404))
            );

    // Scenario 3: Transaction history with filters
    private final ScenarioBuilder transactionHistoryScenario = scenario("Transaction History")
            .exec(
                    http("GET /api/v1/transactions/history")
                            .get("/api/v1/transactions/history")
                            .queryParam("page", "0")
                            .queryParam("size", "20")
                            .queryParam("sortBy", "createdAt")
                            .queryParam("sortDirection", "DESC")
                            .check(status().is(200))
            );

    // Scenario 4: Account balance check
    private final ScenarioBuilder accountBalanceScenario = scenario("Account Balance Check")
            .feed(paymentFeeder)
            .exec(
                    http("GET /api/v1/accounts/{id}")
                            .get("/api/v1/accounts/#{senderAccountId}")
                            .check(status().in(200, 404))
            );

    // Scenario 5: Batch payments
    private final ScenarioBuilder batchPaymentScenario = scenario("Batch Payment")
            .exec(
                    http("POST /api/v1/transactions/batch")
                            .post("/api/v1/transactions/batch")
                            .body(StringBody("""
                                    {
                                      "transactions": [
                                        {
                                          "senderAccountId": "%s",
                                          "receiverAccountId": "%s",
                                          "amount": 50.00,
                                          "currency": "USD",
                                          "type": "TRANSFER",
                                          "idempotencyKey": "%s"
                                        },
                                        {
                                          "senderAccountId": "%s",
                                          "receiverAccountId": "%s",
                                          "amount": 75.00,
                                          "currency": "USD",
                                          "type": "TRANSFER",
                                          "idempotencyKey": "%s"
                                        }
                                      ]
                                    }
                                    """.formatted(
                                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()
                            )))
                            .check(status().in(200, 400))
            );

    // Load model: Ramp up to target TPS
    {
        setUp(
                // Primary payment flow — target 1000+ TPS
                initiatePaymentScenario.injectOpen(
                        rampUsersPerSec(10).to(200).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(200).during(Duration.ofMinutes(2)),
                        rampUsersPerSec(200).to(500).during(Duration.ofSeconds(30)),
                        constantUsersPerSec(500).during(Duration.ofMinutes(2)),
                        rampUsersPerSec(500).to(1000).during(Duration.ofSeconds(60)),
                        constantUsersPerSec(1000).during(Duration.ofMinutes(3)),
                        rampUsersPerSec(1000).to(0).during(Duration.ofSeconds(30))
                ),
                // Read-heavy traffic mix
                getTransactionScenario.injectOpen(
                        constantUsersPerSec(300).during(Duration.ofMinutes(5))
                ),
                transactionHistoryScenario.injectOpen(
                        constantUsersPerSec(100).during(Duration.ofMinutes(5))
                ),
                accountBalanceScenario.injectOpen(
                        constantUsersPerSec(200).during(Duration.ofMinutes(5))
                ),
                batchPaymentScenario.injectOpen(
                        constantUsersPerSec(20).during(Duration.ofMinutes(5))
                )
        ).protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile3().lt(2000),
                        global().responseTime().percentile4().lt(5000),
                        global().successfulRequests().percent().gt(95.0),
                        forAll().failedRequests().percent().lt(5.0)
                );
    }
}
