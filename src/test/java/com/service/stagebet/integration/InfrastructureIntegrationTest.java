package com.service.stagebet.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@Testcontainers
class InfrastructureIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> KAFKA = new GenericContainer<>("apache/kafka:4.0.0")
            .withExposedPorts(9092);

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Test
    void startsTheRequiredInfrastructureBoundaries() {
        assertTrue(POSTGRES.isRunning());
        assertTrue(KAFKA.isRunning());
        assertTrue(REDIS.isRunning());
    }
}
