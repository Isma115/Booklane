package com.booklane.booking.adapters.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.IdClass;

@Entity
@Table(name = "command_results", schema = "booking")
@IdClass(CommandResultId.class)
public class CommandResultEntity {
    @Id @Column(name = "actor_id")
    private UUID actorId;
    @Id @Column(name = "command_id")
    private UUID commandId;
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;
    @Column(name = "command_type", nullable = false, length = 40)
    private String commandType;
    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;
    @Column(length = 20)
    private String outcome;
    @Column(name = "reason_code", length = 64)
    private String reasonCode;
    @Column(name = "booking_id")
    private UUID bookingId;
    @Column(name = "resource_version")
    private Long resourceVersion;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "canonical_request", columnDefinition = "jsonb")
    private String canonicalRequest;
    @Column(name = "already_cancelled")
    private Boolean alreadyCancelled;
    @Column(name = "processed_at")
    private Instant processedAt;

    protected CommandResultEntity() {}
}
