package com.service.stagebet.wallet.application;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletOperationService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public WalletOperationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public WalletOperationResult deposit(WalletCommand command) {
        WalletOperationResult duplicate = findDuplicate(command, "DEPOSIT");
        if (duplicate != null) {
            return duplicate;
        }

        WalletRow wallet = lockWallet(command);
        updateBalance(wallet.id(), command.money().amount(), BigDecimal.ZERO);
        UUID transactionId = insertTransaction(command, "DEPOSIT");
        insertLedgerEntry(transactionId, wallet.id(), "CREDIT", command.money().amount(), "DEPOSIT");
        insertIdempotencyRecord(command, "DEPOSIT");
        return result(command.referenceId(), wallet.id(), wallet.availableBalance().add(command.money().amount()),
                wallet.reservedBalance(), wallet.currency(), false);
    }

    @Transactional
    public WalletOperationResult reserve(WalletCommand command) {
        WalletOperationResult duplicate = findDuplicate(command, "RESERVE");
        if (duplicate != null) {
            return duplicate;
        }

        WalletRow wallet = lockWallet(command);
        if (wallet.availableBalance().compareTo(command.money().amount()) < 0) {
            throw new InsufficientFundsException();
        }

        updateBalance(wallet.id(), command.money().amount().negate(), command.money().amount());
        insertHold(wallet.id(), command);
        UUID transactionId = insertTransaction(command, "RESERVE");
        insertLedgerEntry(transactionId, wallet.id(), "DEBIT", command.money().amount(), "BET_RESERVATION");
        insertIdempotencyRecord(command, "RESERVE");
        return result(command.referenceId(), wallet.id(), wallet.availableBalance().subtract(command.money().amount()),
                wallet.reservedBalance().add(command.money().amount()), wallet.currency(), false);
    }

    @Transactional
    public WalletOperationResult release(UUID tenantId, UUID holdId, UUID actorId, String idempotencyKey) {
        WalletOperationResult duplicate = findDuplicate(tenantId, idempotencyKey, "RELEASE");
        if (duplicate != null) {
            return duplicate;
        }

        HoldRow hold = findHoldForUpdate(tenantId, holdId);
        if (!"ACTIVE".equals(hold.status())) {
            throw new IllegalStateException("only an active hold can be released");
        }

        WalletRow wallet = lockWallet(hold.walletId());
        updateBalance(wallet.id(), hold.amount(), hold.amount().negate());
        updateHoldStatus(holdId, "RELEASED");
        WalletCommand command = new WalletCommand(tenantId, hold.playerId(), holdId, hold.amount(), hold.currency(),
                idempotencyKey, actorId);
        UUID transactionId = insertTransaction(command, "RELEASE");
        insertLedgerEntry(transactionId, wallet.id(), "CREDIT", hold.amount(), "BET_RELEASE");
        insertIdempotencyRecord(command, "RELEASE");
        return result(holdId, wallet.id(), wallet.availableBalance().add(hold.amount()),
                wallet.reservedBalance().subtract(hold.amount()), wallet.currency(), false);
    }

    @Transactional
    public WalletOperationResult capture(UUID tenantId, UUID holdId, UUID actorId, String idempotencyKey) {
        WalletOperationResult duplicate = findDuplicate(tenantId, idempotencyKey, "CAPTURE");
        if (duplicate != null) {
            return duplicate;
        }

        HoldRow hold = findHoldForUpdate(tenantId, holdId);
        if (!"ACTIVE".equals(hold.status())) {
            throw new IllegalStateException("only an active hold can be captured");
        }

        WalletRow wallet = lockWallet(hold.walletId());
        updateBalance(wallet.id(), BigDecimal.ZERO, hold.amount().negate());
        updateHoldStatus(holdId, "CAPTURED");
        WalletCommand command = new WalletCommand(tenantId, hold.playerId(), holdId, hold.amount(), hold.currency(),
                idempotencyKey, actorId);
        insertTransaction(command, "CAPTURE");
        insertIdempotencyRecord(command, "CAPTURE");
        return result(holdId, wallet.id(), wallet.availableBalance(), wallet.reservedBalance(), wallet.currency(), false);
    }

    @Transactional
    public WalletOperationResult payout(WalletCommand command) {
        WalletOperationResult duplicate = findDuplicate(command, "PAYOUT");
        if (duplicate != null) {
            return duplicate;
        }

        WalletRow wallet = lockWallet(command);
        updateBalance(wallet.id(), command.money().amount(), BigDecimal.ZERO);
        UUID transactionId = insertTransaction(command, "PAYOUT");
        insertLedgerEntry(transactionId, wallet.id(), "CREDIT", command.money().amount(), "PAYOUT");
        insertIdempotencyRecord(command, "PAYOUT");
        return result(command.referenceId(), wallet.id(), wallet.availableBalance().add(command.money().amount()),
                wallet.reservedBalance(), wallet.currency(), false);
    }

    @Transactional(readOnly = true)
    public WalletBalance getBalance(UUID tenantId, UUID playerId, String currency) {
        WalletRow wallet = readWallet(tenantId, playerId, currency);
        return new WalletBalance(wallet.id(), wallet.availableBalance(), wallet.reservedBalance(), wallet.currency());
    }

    @Transactional(readOnly = true)
    public ReconciliationResult reconcile(UUID tenantId, UUID playerId, String currency) {
        WalletRow wallet = readWallet(tenantId, playerId, currency);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("walletId", wallet.id());
        BigDecimal ledgerAvailableBalance = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE -amount END), 0)
                FROM ledger_entries
                WHERE wallet_account_id = :walletId
                """, parameters, BigDecimal.class);
        BigDecimal activeHoldBalance = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM wallet_holds
                WHERE wallet_account_id = :walletId AND status = 'ACTIVE'
                """, parameters, BigDecimal.class);
        boolean balanced = wallet.availableBalance().compareTo(ledgerAvailableBalance) == 0
                && wallet.reservedBalance().compareTo(activeHoldBalance) == 0;
        return new ReconciliationResult(wallet.id(), wallet.availableBalance(), ledgerAvailableBalance,
                wallet.reservedBalance(), activeHoldBalance, balanced);
    }

    private WalletOperationResult findDuplicate(WalletCommand command, String operation) {
        return findDuplicate(command.tenantId(), command.idempotencyKey(), operation);
    }

    private WalletOperationResult findDuplicate(UUID tenantId, String idempotencyKey, String operation) {
        String sql = """
                SELECT i.result_reference_id, w.id AS wallet_id, w.available_balance, w.reserved_balance, w.currency
                FROM idempotency_records i
                LEFT JOIN wallet_accounts w ON w.tenant_id = i.tenant_id AND w.id = COALESCE(
                    (SELECT wallet_account_id FROM wallet_holds h WHERE h.id = i.result_reference_id),
                    (SELECT le.wallet_account_id FROM ledger_entries le
                     JOIN ledger_transactions lt ON lt.id = le.transaction_id
                     WHERE lt.tenant_id = i.tenant_id AND lt.reference_id = i.result_reference_id
                     LIMIT 1)
                )
                WHERE i.tenant_id = :tenantId AND i.idempotency_key = :idempotencyKey AND i.operation = :operation
                """;
        return jdbcTemplate.query(sql, params(tenantId, idempotencyKey, operation), rows -> {
            if (!rows.next()) {
                return null;
            }
            return result(rows.getObject("result_reference_id", UUID.class),
                    rows.getObject("wallet_id", UUID.class),
                    valueOrZero(rows, "available_balance"), valueOrZero(rows, "reserved_balance"),
                    rows.getString("currency"), true);
        });
    }

    private WalletRow lockWallet(WalletCommand command) {
        ensureWallet(command);
        return lockWallet(command.tenantId(), command.playerId(), command.money().currency());
    }

    private WalletRow lockWallet(UUID walletId) {
        return jdbcTemplate.queryForObject("SELECT id, tenant_id, player_id, currency, available_balance, reserved_balance "
                        + "FROM wallet_accounts WHERE id = :walletId FOR UPDATE",
                new MapSqlParameterSource("walletId", walletId), this::walletRow);
    }

    private WalletRow lockWallet(UUID tenantId, UUID playerId, String currency) {
        return jdbcTemplate.queryForObject("SELECT id, tenant_id, player_id, currency, available_balance, reserved_balance "
                        + "FROM wallet_accounts WHERE tenant_id = :tenantId AND player_id = :playerId AND currency = :currency FOR UPDATE",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("playerId", playerId)
                        .addValue("currency", currency), this::walletRow);
    }

    private WalletRow readWallet(UUID tenantId, UUID playerId, String currency) {
        return jdbcTemplate.queryForObject("SELECT id, tenant_id, player_id, currency, available_balance, reserved_balance "
                        + "FROM wallet_accounts WHERE tenant_id = :tenantId AND player_id = :playerId AND currency = :currency",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("playerId", playerId)
                        .addValue("currency", currency), this::walletRow);
    }

    private void ensureWallet(WalletCommand command) {
        String sql = "INSERT INTO wallet_accounts (id, tenant_id, player_id, currency) VALUES (:id, :tenantId, :playerId, :currency) ON CONFLICT (tenant_id, player_id, currency) DO NOTHING";
        jdbcTemplate.update(sql, new MapSqlParameterSource().addValue("id", UUID.randomUUID())
                .addValue("tenantId", command.tenantId()).addValue("playerId", command.playerId())
                .addValue("currency", command.money().currency()));
    }

    private void updateBalance(UUID walletId, BigDecimal availableDelta, BigDecimal reservedDelta) {
        int updated = jdbcTemplate.update("UPDATE wallet_accounts SET available_balance = available_balance + :availableDelta, "
                        + "reserved_balance = reserved_balance + :reservedDelta, version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = :walletId AND available_balance + :availableDelta >= 0 AND reserved_balance + :reservedDelta >= 0",
                new MapSqlParameterSource().addValue("walletId", walletId).addValue("availableDelta", availableDelta)
                        .addValue("reservedDelta", reservedDelta));
        if (updated != 1) {
            throw new IllegalStateException("wallet balance update violated a balance constraint");
        }
    }

    private void insertHold(UUID walletId, WalletCommand command) {
        jdbcTemplate.update("INSERT INTO wallet_holds (id, wallet_account_id, amount, status, reference_type, reference_id) "
                        + "VALUES (:id, :walletId, :amount, 'ACTIVE', 'BET', :referenceId)",
                new MapSqlParameterSource().addValue("id", command.referenceId()).addValue("walletId", walletId)
                        .addValue("amount", command.money().amount()).addValue("referenceId", command.referenceId()));
    }

    private void updateHoldStatus(UUID holdId, String status) {
        jdbcTemplate.update("UPDATE wallet_holds SET status = :status, released_at = CASE WHEN :status = 'RELEASED' "
                        + "THEN CURRENT_TIMESTAMP ELSE released_at END WHERE id = :holdId",
                new MapSqlParameterSource().addValue("holdId", holdId).addValue("status", status));
    }

    private HoldRow findHoldForUpdate(UUID tenantId, UUID holdId) {
        return jdbcTemplate.queryForObject("SELECT h.id, h.wallet_account_id, h.amount, h.status, w.tenant_id, w.player_id, w.currency "
                        + "FROM wallet_holds h JOIN wallet_accounts w ON w.id = h.wallet_account_id "
                        + "WHERE w.tenant_id = :tenantId AND h.id = :holdId FOR UPDATE",
                new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("holdId", holdId),
                (rs, rowNum) -> new HoldRow(rs.getObject("id", UUID.class), rs.getObject("wallet_account_id", UUID.class),
                        rs.getBigDecimal("amount"), rs.getString("status"), rs.getObject("player_id", UUID.class),
                        rs.getString("currency")));
    }

    private UUID insertTransaction(WalletCommand command, String transactionType) {
        UUID transactionId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO ledger_transactions (id, tenant_id, idempotency_key, transaction_type, reference_type, reference_id, actor_id) "
                        + "VALUES (:id, :tenantId, :idempotencyKey, :transactionType, 'WALLET_OPERATION', :referenceId, :actorId)",
                new MapSqlParameterSource().addValue("id", transactionId).addValue("tenantId", command.tenantId())
                        .addValue("idempotencyKey", command.idempotencyKey()).addValue("transactionType", transactionType)
                        .addValue("referenceId", command.referenceId()).addValue("actorId", command.actorId()));
        return transactionId;
    }

    private void insertLedgerEntry(UUID transactionId, UUID walletId, String entryType, BigDecimal amount, String reason) {
        jdbcTemplate.update("INSERT INTO ledger_entries (id, transaction_id, wallet_account_id, entry_type, amount, reason) "
                        + "VALUES (:id, :transactionId, :walletId, :entryType, :amount, :reason)",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("transactionId", transactionId)
                        .addValue("walletId", walletId).addValue("entryType", entryType).addValue("amount", amount)
                        .addValue("reason", reason));
    }

    private void insertIdempotencyRecord(WalletCommand command, String operation) {
        jdbcTemplate.update("INSERT INTO idempotency_records (id, tenant_id, idempotency_key, operation, request_hash, result_reference_id) "
                        + "VALUES (:id, :tenantId, :idempotencyKey, :operation, :requestHash, :resultReferenceId)",
                new MapSqlParameterSource().addValue("id", UUID.randomUUID()).addValue("tenantId", command.tenantId())
                        .addValue("idempotencyKey", command.idempotencyKey()).addValue("operation", operation)
                        .addValue("requestHash", "0000000000000000000000000000000000000000000000000000000000000000")
                        .addValue("resultReferenceId", command.referenceId()));
    }

    private MapSqlParameterSource params(UUID tenantId, String idempotencyKey, String operation) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("idempotencyKey", idempotencyKey)
                .addValue("operation", operation);
    }

    private WalletRow walletRow(ResultSet rs, int rowNum) throws SQLException {
        return new WalletRow(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("player_id", UUID.class), rs.getString("currency"),
                rs.getBigDecimal("available_balance"), rs.getBigDecimal("reserved_balance"));
    }

    private BigDecimal valueOrZero(ResultSet resultSet, String column) throws SQLException {
        BigDecimal value = resultSet.getBigDecimal(column);
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private WalletOperationResult result(UUID referenceId, UUID walletId, BigDecimal availableBalance,
            BigDecimal reservedBalance, String currency, boolean duplicate) {
        return new WalletOperationResult(referenceId, walletId, availableBalance, reservedBalance, currency, duplicate);
    }

    private record WalletRow(UUID id, UUID tenantId, UUID playerId, String currency,
            BigDecimal availableBalance, BigDecimal reservedBalance) {
    }

    private record HoldRow(UUID id, UUID walletId, BigDecimal amount, String status,
            UUID playerId, String currency) {
    }
}
