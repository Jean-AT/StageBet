package com.service.stagebet.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.service.stagebet.betting.application.BettingService;
import com.service.stagebet.game.application.CrashRoundEngine;
import com.service.stagebet.game.domain.RoundStatus;
import com.service.stagebet.wallet.application.WalletBalance;
import com.service.stagebet.wallet.application.WalletCommand;
import com.service.stagebet.wallet.application.WalletOperationService;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"stagebet.rate-limit.max-requests=2", "stagebet.rate-limit.window=PT10M"})
class HundredPlayersGameIntegrationTest {

    private static final int PLAYER_COUNT = 100;
    private static final int CASH_OUT_COUNT = 50;
    private static final int WORKER_COUNT = 20;
    private static final int EXPECTED_EVENTS = 4 + PLAYER_COUNT + CASH_OUT_COUNT + CASH_OUT_COUNT;
    private static final BigDecimal STAKE = new BigDecimal("10.00");

    @Autowired private MockMvc mockMvc;
    @Autowired private CrashRoundEngine roundEngine;
    @Autowired private BettingService bettingService;
    @Autowired private WalletOperationService wallet;
    @Autowired private StringRedisTemplate redis;
    @Autowired private NamedParameterJdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    @Value("${spring.kafka.bootstrap-servers}") private String kafkaBootstrapServers;
    @Value("${stagebet.messaging.topic}") private String eventTopic;

    @Test
    void hundredPlayersCompleteOneGameThroughHttpRedisPostgresAndKafka() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        List<PlayerBet> players = players();

        try (KafkaConsumer<String, String> consumer = kafkaConsumer()) {
            subscribeFromEnd(consumer);
            for (PlayerBet player : players) {
                wallet.deposit(new WalletCommand(tenantId, player.playerId(), player.betId(), STAKE,
                        "USD", "hundred-players-deposit-" + player.betId(), null));
            }
            roundEngine.scheduleRound(roundId);
            roundEngine.openRound(roundId);

            placeBetsConcurrently(tenantId, roundId, players);
            assertEquals(PLAYER_COUNT, countBets(roundId, "ACCEPTED"));
            for (PlayerBet player : players) {
                assertBalance(tenantId, player.playerId(), "0.00", "10.00");
                assertEquals("1", redis.opsForValue().get(rateLimitKey(tenantId, player.playerId())));
            }

            roundEngine.startRound(roundId);
            for (int index = 0; index < CASH_OUT_COUNT; index++) {
                PlayerBet player = players.get(index);
                int status = mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/bets/{betId}/cash-out",
                                tenantId, player.playerId(), player.betId())
                        .with(user(player.playerId().toString()))
                        .header("X-Idempotency-Key", "hundred-players-cash-out-" + player.betId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRoundSequence\":2}"))
                        .andReturn().getResponse().getStatus();
                assertEquals(200, status, "cash-out failed for player " + player.playerId());
            }
            PlayerBet rateLimitedPlayer = players.get(0);
            int rateLimitedStatus = mockMvc.perform(post(
                            "/api/tenants/{tenantId}/players/{playerId}/bets/{betId}/cash-out",
                            tenantId, rateLimitedPlayer.playerId(), rateLimitedPlayer.betId())
                    .with(user(rateLimitedPlayer.playerId().toString()))
                    .header("X-Idempotency-Key", "hundred-players-extra-" + rateLimitedPlayer.betId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"expectedRoundSequence\":2}"))
                    .andReturn().getResponse().getStatus();
            assertEquals(429, rateLimitedStatus, "Redis should reject a third command for one player");
            roundEngine.crashRound(roundId);
            assertEquals(CASH_OUT_COUNT, bettingService.settleCrashedRound(tenantId, roundId, null));
            roundEngine.settleRound(roundId);

            assertEquals(RoundStatus.SETTLED, roundEngine.findRound(roundId).orElseThrow().status());
            assertEquals(CASH_OUT_COUNT, countBets(roundId, "CASHED_OUT"));
            assertEquals(CASH_OUT_COUNT, countBets(roundId, "LOST"));
            for (int index = 0; index < players.size(); index++) {
                PlayerBet player = players.get(index);
                assertBalance(tenantId, player.playerId(), index < CASH_OUT_COUNT ? "10.00" : "0.00", "0.00");
                assertTrue(wallet.reconcile(tenantId, player.playerId(), "USD").balanced(),
                        "ledger mismatch for player " + player.playerId());
                assertEquals(index == 0 ? "3" : index < CASH_OUT_COUNT ? "2" : "1",
                        redis.opsForValue().get(rateLimitKey(tenantId, player.playerId())));
            }
            assertEquals(EXPECTED_EVENTS, countOutboxEvents(roundId));
            assertKafkaEvents(consumer, roundId, players);
        }
    }

    private List<PlayerBet> players() {
        List<PlayerBet> players = new ArrayList<>();
        for (int index = 0; index < PLAYER_COUNT; index++) {
            players.add(new PlayerBet(UUID.randomUUID(), UUID.randomUUID()));
        }
        return players;
    }

    private void placeBetsConcurrently(UUID tenantId, UUID roundId, List<PlayerBet> players) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch ready = new CountDownLatch(WORKER_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> responses = new ArrayList<>();
        try {
            for (PlayerBet player : players) {
                responses.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post("/api/tenants/{tenantId}/players/{playerId}/rounds/{roundId}/bets",
                                    tenantId, player.playerId(), roundId)
                            .with(user(player.playerId().toString()))
                            .header("X-Idempotency-Key", "hundred-players-bet-" + player.betId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"betId\":\"" + player.betId()
                                    + "\",\"amount\":\"10.00\",\"currency\":\"USD\",\"expectedRoundSequence\":1}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "bet workers did not start");
            start.countDown();
            for (int index = 0; index < responses.size(); index++) {
                assertEquals(200, responses.get(index).get(90, TimeUnit.SECONDS),
                        "bet failed for player " + players.get(index).playerId());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private KafkaConsumer<String, String> kafkaConsumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "hundred-players-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"),
                new StringDeserializer(), new StringDeserializer());
    }

    private void subscribeFromEnd(KafkaConsumer<String, String> consumer) {
        consumer.subscribe(List.of(eventTopic));
        Instant deadline = Instant.now().plusSeconds(20);
        while (consumer.assignment().isEmpty() && Instant.now().isBefore(deadline)) {
            consumer.poll(Duration.ofMillis(250));
        }
        assertTrue(!consumer.assignment().isEmpty(), "Kafka topic has no assigned partitions");
        consumer.endOffsets(consumer.assignment()).forEach(consumer::seek);
    }

    private void assertKafkaEvents(KafkaConsumer<String, String> consumer, UUID roundId,
            List<PlayerBet> players) {
        Set<UUID> expectedBets = new HashSet<>();
        players.forEach(player -> expectedBets.add(player.betId()));
        Set<UUID> eventIds = new HashSet<>();
        Map<String, Integer> eventTypes = new HashMap<>();
        Set<UUID> acceptedBets = new HashSet<>();
        Instant deadline = Instant.now().plusSeconds(60);
        while (eventIds.size() < EXPECTED_EVENTS && Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
                JsonNode envelope = objectMapper.readTree(record.value());
                if (!roundId.toString().equals(envelope.path("correlationId").asText())) {
                    continue;
                }
                UUID eventId = UUID.fromString(envelope.path("eventId").asText());
                assertTrue(eventIds.add(eventId), "duplicate Kafka event " + eventId);
                String eventType = envelope.path("eventType").asText();
                eventTypes.merge(eventType, 1, Integer::sum);
                if (eventType.startsWith("bet.")) {
                    UUID betId = UUID.fromString(envelope.path("payload").path("betId").asText());
                    assertTrue(expectedBets.contains(betId), "unexpected bet in Kafka: " + betId);
                    if ("bet.accepted.v1".equals(eventType)) {
                        acceptedBets.add(betId);
                    }
                }
            }
        }
        assertEquals(EXPECTED_EVENTS, eventIds.size(), "missing Kafka events: " + eventTypes);
        assertEquals(expectedBets, acceptedBets);
        assertEquals(PLAYER_COUNT, eventTypes.get("bet.accepted.v1"));
        assertEquals(CASH_OUT_COUNT, eventTypes.get("bet.cashed_out.v1"));
        assertEquals(CASH_OUT_COUNT, eventTypes.get("bet.lost.v1"));
        for (String status : List.of("open", "running", "crashed", "settled")) {
            assertEquals(1, eventTypes.get("round." + status + ".v1"));
        }
    }

    private String rateLimitKey(UUID tenantId, UUID playerId) {
        return "stagebet:rate-limit:betting:" + tenantId + ":" + playerId;
    }

    private int countBets(UUID roundId, String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM bets WHERE round_id = :roundId AND status = :status",
                new MapSqlParameterSource().addValue("roundId", roundId).addValue("status", status), Integer.class);
    }

    private int countOutboxEvents(UUID roundId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE correlation_id = :roundId",
                new MapSqlParameterSource("roundId", roundId), Integer.class);
    }

    private void assertBalance(UUID tenantId, UUID playerId, String available, String reserved) {
        WalletBalance balance = wallet.getBalance(tenantId, playerId, "USD");
        assertEquals(new BigDecimal(available), balance.availableBalance());
        assertEquals(new BigDecimal(reserved), balance.reservedBalance());
    }

    private record PlayerBet(UUID playerId, UUID betId) {
    }
}
