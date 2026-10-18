package com.service.stagebet.payments.infrastructure;

import com.service.stagebet.payments.application.PaymentStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcPaymentStore implements PaymentStore {

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPaymentStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StoredPaymentIntent> findPaymentIntent(UUID tenantId, String idempotencyKey) {
        return findPaymentIntent("tenant_id = :tenantId AND idempotency_key = :idempotencyKey",
                parameters(tenantId).addValue("idempotencyKey", idempotencyKey));
    }

    @Override
    public Optional<StoredPaymentIntent> findPaymentIntent(UUID tenantId, UUID paymentId) {
        return findPaymentIntent("tenant_id = :tenantId AND id = :paymentId",
                parameters(tenantId).addValue("paymentId", paymentId));
    }

    @Override
    public StoredPaymentIntent savePaymentIntent(StoredPaymentIntent paymentIntent) {
        jdbc.update("""
                INSERT INTO payment_intents (id, tenant_id, player_id, provider, provider_reference, amount, currency, status, idempotency_key)
                VALUES (:paymentId, :tenantId, :playerId, :provider, :providerReference, :amount, :currency, :status, :idempotencyKey)
                """, paymentIntentParameters(paymentIntent));
        return paymentIntent;
    }

    @Override
    public Optional<StoredRefund> findRefund(UUID tenantId, UUID paymentId, String idempotencyKey) {
        return jdbc.query("""
                SELECT id, tenant_id, payment_intent_id, provider, provider_reference, amount, currency, status, idempotency_key
                FROM payment_refunds
                WHERE tenant_id = :tenantId AND payment_intent_id = :paymentId AND idempotency_key = :idempotencyKey
                """, parameters(tenantId).addValue("paymentId", paymentId).addValue("idempotencyKey", idempotencyKey),
                rows -> rows.next() ? Optional.of(refund(rows)) : Optional.empty());
    }

    @Override
    public StoredRefund saveRefund(StoredRefund refund) {
        jdbc.update("""
                INSERT INTO payment_refunds (id, tenant_id, payment_intent_id, provider, provider_reference, amount, currency, status, idempotency_key)
                VALUES (:refundId, :tenantId, :paymentId, :provider, :providerReference, :amount, :currency, :status, :idempotencyKey)
                """, refundParameters(refund));
        return refund;
    }

    @Override
    public boolean updatePaymentIntentStatus(String provider, String providerReference, String status) {
        return jdbc.update("""
                UPDATE payment_intents
                SET status = :status
                WHERE provider = :provider AND provider_reference = :providerReference
                """, new MapSqlParameterSource().addValue("provider", provider)
                .addValue("providerReference", providerReference).addValue("status", status)) == 1;
    }

    private Optional<StoredPaymentIntent> findPaymentIntent(String predicate, MapSqlParameterSource parameters) {
        return jdbc.query("SELECT id, tenant_id, player_id, provider, provider_reference, amount, currency, status, idempotency_key "
                        + "FROM payment_intents WHERE " + predicate, parameters,
                rows -> rows.next() ? Optional.of(paymentIntent(rows)) : Optional.empty());
    }

    private MapSqlParameterSource parameters(UUID tenantId) {
        return new MapSqlParameterSource("tenantId", tenantId);
    }

    private MapSqlParameterSource paymentIntentParameters(StoredPaymentIntent paymentIntent) {
        return new MapSqlParameterSource().addValue("paymentId", paymentIntent.paymentId())
                .addValue("tenantId", paymentIntent.tenantId()).addValue("playerId", paymentIntent.playerId())
                .addValue("provider", paymentIntent.provider()).addValue("providerReference", paymentIntent.providerReference())
                .addValue("amount", paymentIntent.amount()).addValue("currency", paymentIntent.currency())
                .addValue("status", paymentIntent.status()).addValue("idempotencyKey", paymentIntent.idempotencyKey());
    }

    private MapSqlParameterSource refundParameters(StoredRefund refund) {
        return new MapSqlParameterSource().addValue("refundId", refund.refundId()).addValue("tenantId", refund.tenantId())
                .addValue("paymentId", refund.paymentId()).addValue("provider", refund.provider())
                .addValue("providerReference", refund.providerReference()).addValue("amount", refund.amount())
                .addValue("currency", refund.currency()).addValue("status", refund.status())
                .addValue("idempotencyKey", refund.idempotencyKey());
    }

    private StoredPaymentIntent paymentIntent(ResultSet rows) throws SQLException {
        return new StoredPaymentIntent(rows.getObject("id", UUID.class), rows.getObject("tenant_id", UUID.class),
                rows.getObject("player_id", UUID.class), rows.getString("provider"), rows.getString("provider_reference"),
                rows.getBigDecimal("amount"), rows.getString("currency"), rows.getString("status"), rows.getString("idempotency_key"));
    }

    private StoredRefund refund(ResultSet rows) throws SQLException {
        return new StoredRefund(rows.getObject("id", UUID.class), rows.getObject("tenant_id", UUID.class),
                rows.getObject("payment_intent_id", UUID.class), rows.getString("provider"), rows.getString("provider_reference"),
                rows.getBigDecimal("amount"), rows.getString("currency"), rows.getString("status"), rows.getString("idempotency_key"));
    }
}
