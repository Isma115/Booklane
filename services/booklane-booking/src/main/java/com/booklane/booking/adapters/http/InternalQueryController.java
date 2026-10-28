package com.booklane.booking.adapters.http;

import com.booklane.booking.domain.Actor;
import com.booklane.booking.query.BookingReadService;
import com.booklane.booking.query.ReadModels.*;
import com.booklane.booking.security.InternalPrincipalFilter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public class InternalQueryController {
    private final BookingReadService reads;

    public InternalQueryController(BookingReadService reads) { this.reads = reads; }

    @GetMapping("/resources")
    Page<Resource> resources(@RequestAttribute(InternalPrincipalFilter.ACTOR_ATTRIBUTE) Actor actor,
            @RequestParam(required = false) String type, @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return reads.resources(actor, type, q, page, size, sort);
    }

    @GetMapping("/resources/{id}/availability")
    Availability availability(@RequestAttribute(InternalPrincipalFilter.ACTOR_ATTRIBUTE) Actor actor,
            @PathVariable String id, @RequestParam String from, @RequestParam String to) {
        return reads.availability(actor, uuid(id), LocalDate.parse(from), LocalDate.parse(to))
                .orElseThrow(() -> new HttpFailure(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @GetMapping("/bookings/mine")
    Page<Booking> mine(@RequestAttribute(InternalPrincipalFilter.ACTOR_ATTRIBUTE) Actor actor,
            @RequestParam(required = false) String status, @RequestParam(defaultValue = "FUTURE") String period,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startsAt,asc") String sort) {
        return reads.mine(actor, status, period, page, size, sort);
    }

    @GetMapping("/bookings/{id}")
    Booking booking(@RequestAttribute(InternalPrincipalFilter.ACTOR_ATTRIBUTE) Actor actor, @PathVariable String id) {
        return reads.booking(actor, uuid(id)).orElseThrow(() -> new HttpFailure(HttpStatus.NOT_FOUND, "NOT_FOUND"));
    }

    @GetMapping("/operations/{id}")
    Operation operation(@RequestAttribute(InternalPrincipalFilter.ACTOR_ATTRIBUTE) Actor actor, @PathVariable String id,
            @RequestParam(required = false) String requestHash) {
        var lookup = reads.operation(actor, uuid(id), requestHash);
        return switch (lookup.state()) {
            case FOUND -> lookup.result();
            case NOT_OBSERVED -> throw new HttpFailure(HttpStatus.NOT_FOUND, "OPERATION_NOT_OBSERVED");
            case IDEMPOTENCY_CONFLICT -> throw new HttpFailure(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT");
        };
    }

    @GetMapping("/audit")
    Page<AuditEntry> audit(@RequestAttribute(InternalPrincipalFilter.ACTOR_ATTRIBUTE) Actor actor,
            @RequestParam(required = false) String resourceId, @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String action, @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt,desc") String sort) {
        return reads.audit(actor, new AuditFilter(optionalUuid(resourceId), optionalUuid(actorId), action, outcome,
                optionalInstant(from), optionalInstant(to)), page, size, sort);
    }

    private static UUID uuid(String value) {
        UUID id = UUID.fromString(value);
        if (!id.toString().equals(value)) throw new IllegalArgumentException("invalid UUID");
        return id;
    }
    private static UUID optionalUuid(String value) { return value == null ? null : uuid(value); }
    private static Instant optionalInstant(String value) {
        if (value == null) return null;
        if (!value.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(Z|[+-]\\d{2}:\\d{2})")) {
            throw new IllegalArgumentException("invalid timestamp");
        }
        return OffsetDateTime.parse(value).toInstant();
    }
}
