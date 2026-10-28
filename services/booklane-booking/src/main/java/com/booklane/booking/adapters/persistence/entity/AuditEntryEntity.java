package com.booklane.booking.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_entries", schema = "booking")
public class AuditEntryEntity {
    @Id
    private UUID id;
    @Column(name = "actor_id", nullable = false)
    private UUID actorId;
    @Column(name = "command_id", nullable = false)
    private UUID commandId;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(nullable = false, length = 40)
    private String action;
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;
    @Column(name = "booking_id")
    private UUID bookingId;
    @Column(nullable = false, length = 20)
    private String outcome;
    @Column(length = 500)
    private String reason;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntryEntity() {}
}
