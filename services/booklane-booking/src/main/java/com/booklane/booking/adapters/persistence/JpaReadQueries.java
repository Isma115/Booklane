package com.booklane.booking.adapters.persistence;

import com.booklane.booking.query.ReadModels.*;
import com.booklane.booking.query.ReadStore;
import com.booklane.contracts.WireJson;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

@Repository
public class JpaReadQueries implements ReadStore {
    private static final String RESOURCE_COLUMNS = "id, code, name, type, capacity, description, active, version";
    private static final String BOOKING_COLUMNS = "id, resource_id, owner_id, title, starts_at, ends_at, status, created_at, cancelled_at, version";
    private final NamedParameterJdbcTemplate jdbc;

    public JpaReadQueries(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Page<Resource> resources(String type, String search, int page, int size) {
        var params = new MapSqlParameterSource();
        String where = " WHERE true";
        if (type != null) { where += " AND type = :type"; params.addValue("type", type); }
        if (search != null && !search.isBlank()) {
            where += " AND (name ILIKE :search ESCAPE '!' OR code ILIKE :search ESCAPE '!')";
            params.addValue("search", "%" + search.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        long total = count("SELECT count(*) FROM booking.resources" + where, params);
        pagination(params, page, size);
        var rows = jdbc.query("SELECT " + RESOURCE_COLUMNS + " FROM booking.resources" + where
                + " ORDER BY name ASC, id ASC LIMIT :limit OFFSET :offset", params, RESOURCE);
        return new Page<>(rows, page, size, total);
    }

    @Override
    public Optional<Resource> resource(UUID id) {
        return first(jdbc.query("SELECT " + RESOURCE_COLUMNS + " FROM booking.resources WHERE id = :id",
                new MapSqlParameterSource("id", id), RESOURCE));
    }

    @Override
    public List<OccupiedInterval> occupied(UUID resourceId, Instant from, Instant to) {
        var params = new MapSqlParameterSource("resource", resourceId).addValue("from", date(from)).addValue("to", date(to));
        return jdbc.query("""
                SELECT starts_at, ends_at FROM booking.bookings
                WHERE resource_id = :resource AND status = 'CONFIRMED' AND starts_at < :to AND ends_at > :from
                ORDER BY starts_at ASC, id ASC
                """, params, (row, index) -> new OccupiedInterval(instant(row, "starts_at"), instant(row, "ends_at")));
    }

    @Override
    public Page<Booking> mine(UUID actorId, String status, String period, Instant now, int page, int size, boolean descending) {
        var params = new MapSqlParameterSource("actor", actorId);
        String where = " WHERE owner_id = :actor";
        if (status != null) { where += " AND status = :status"; params.addValue("status", status); }
        if (!"ALL".equals(period)) {
            where += "FUTURE".equals(period) ? " AND starts_at > :now" : " AND starts_at <= :now";
            params.addValue("now", date(now));
        }
        long total = count("SELECT count(*) FROM booking.bookings" + where, params);
        pagination(params, page, size);
        String direction = descending ? " DESC" : " ASC";
        var rows = jdbc.query("SELECT " + BOOKING_COLUMNS + " FROM booking.bookings" + where
                + " ORDER BY starts_at" + direction + ", id" + direction + " LIMIT :limit OFFSET :offset", params, BOOKING);
        return new Page<>(rows, page, size, total);
    }

    @Override
    public Optional<Booking> booking(UUID id, UUID actorId, boolean admin) {
        var params = new MapSqlParameterSource("id", id).addValue("actor", actorId);
        String ownership = admin ? "" : " AND owner_id = :actor";
        return first(jdbc.query("SELECT " + BOOKING_COLUMNS + " FROM booking.bookings WHERE id = :id" + ownership, params, BOOKING));
    }

    @Override
    public Optional<Operation> operation(UUID actorId, UUID commandId) {
        var params = new MapSqlParameterSource("actor", actorId).addValue("command", commandId);
        return first(jdbc.query("""
                SELECT command_id, request_hash, outcome, reason_code, resource_id, booking_id,
                    resource_version, processed_at, canonical_request::text AS canonical_request, already_cancelled
                FROM booking.command_results WHERE actor_id = :actor AND command_id = :command
                """, params, (row, index) -> new Operation(uuid(row, "command_id"), row.getString("request_hash"),
                row.getString("outcome"), row.getString("reason_code"), uuid(row, "resource_id"), uuid(row, "booking_id"),
                (Long) row.getObject("resource_version"), instant(row, "processed_at"),
                WireJson.read(row.getString("canonical_request"), JsonNode.class), (Boolean) row.getObject("already_cancelled"))));
    }

    @Override
    public Page<AuditEntry> audit(AuditFilter filter, int page, int size, boolean descending) {
        var params = new MapSqlParameterSource();
        String where = " WHERE true";
        if (filter.resourceId() != null) { where += " AND resource_id = :resource"; params.addValue("resource", filter.resourceId()); }
        if (filter.actorId() != null) { where += " AND actor_id = :actor"; params.addValue("actor", filter.actorId()); }
        if (filter.action() != null) { where += " AND action = :action"; params.addValue("action", filter.action()); }
        if (filter.outcome() != null) { where += " AND outcome = :outcome"; params.addValue("outcome", filter.outcome()); }
        if (filter.from() != null) { where += " AND occurred_at >= :from"; params.addValue("from", date(filter.from())); }
        if (filter.to() != null) { where += " AND occurred_at < :to"; params.addValue("to", date(filter.to())); }
        long total = count("SELECT count(*) FROM booking.audit_entries" + where, params);
        pagination(params, page, size);
        String direction = descending ? " DESC" : " ASC";
        var rows = jdbc.query("""
                SELECT id, actor_id, command_id, request_hash, action, resource_id, booking_id,
                       outcome, reason_code, reason, occurred_at FROM booking.audit_entries
                """ + where + " ORDER BY occurred_at" + direction + ", id" + direction + " LIMIT :limit OFFSET :offset", params,
                (row, index) -> new AuditEntry(uuid(row, "id"), uuid(row, "actor_id"), uuid(row, "command_id"),
                        row.getString("request_hash"), row.getString("action"), uuid(row, "resource_id"), uuid(row, "booking_id"),
                        row.getString("outcome"), row.getString("reason_code"), row.getString("reason"), instant(row, "occurred_at")));
        return new Page<>(rows, page, size, total);
    }

    private static final RowMapper<Resource> RESOURCE = (row, index) -> new Resource(uuid(row, "id"), row.getString("code"),
            row.getString("name"), row.getString("type"), (Integer) row.getObject("capacity"), row.getString("description"),
            row.getBoolean("active"), row.getLong("version"), null);
    private static final RowMapper<Booking> BOOKING = (row, index) -> new Booking(uuid(row, "id"), uuid(row, "resource_id"),
            uuid(row, "owner_id"), row.getString("title"), instant(row, "starts_at"), instant(row, "ends_at"), row.getString("status"),
            instant(row, "created_at"), instant(row, "cancelled_at"), row.getLong("version"));

    private long count(String sql, MapSqlParameterSource params) { return jdbc.queryForObject(sql, params, Long.class); }
    private static void pagination(MapSqlParameterSource params, int page, int size) {
        params.addValue("limit", size).addValue("offset", (long) page * size);
    }
    private static OffsetDateTime date(Instant value) { return value.atOffset(ZoneOffset.UTC); }
    private static UUID uuid(ResultSet row, String column) throws SQLException { return row.getObject(column, UUID.class); }
    private static Instant instant(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
    private static <T> Optional<T> first(List<T> rows) { return rows.stream().findFirst(); }
}
