package com.booklane.contracts;

import com.booklane.contracts.command.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestFingerprintTest {
    private final UUID resource = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private final UUID booking = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private CreateBookingPayload create(String title) {
        return new CreateBookingPayload(OffsetDateTime.parse("2026-10-28T09:00:00+01:00"),
                OffsetDateTime.parse("2026-10-28T10:00:00+01:00"), title);
    }

    @Test void goldenCreateAndEquivalentOffsetsAndText() {
        var a = create("  Reunión Prisma  ");
        var b = new CreateBookingPayload(OffsetDateTime.parse("2026-10-28T08:00:00Z"),
                OffsetDateTime.parse("2026-10-28T09:00:00Z"), "Reunión Prisma");
        String expected = "3bd360463c43aa7e2f23b157e6d280740b6ebbe49dec7d4ef5a04857d2ccece0";
        assertEquals(expected, RequestFingerprint.calculate(CommandType.CreateBooking, resource, a));
        assertEquals(expected, RequestFingerprint.calculate(CommandType.CreateBooking, resource, b));
        assertEquals("{\"type\":\"CreateBooking\",\"resourceId\":\"" + resource
                + "\",\"payload\":{\"startsAt\":\"2026-10-28T08:00:00Z\",\"endsAt\":\"2026-10-28T09:00:00Z\",\"title\":\"Reunión Prisma\"}}",
                RequestFingerprint.canonicalJson(CommandType.CreateBooking, resource, a));
    }

    @Test void goldenCancelAndStatus() {
        assertEquals("892f80e0d70ff2b50f725441e653c4a5b4ba07eb4a9c492d77ad73b72cba4cf9",
                RequestFingerprint.calculate(CommandType.CancelBooking, resource, new CancelBookingPayload(booking, null)));
        assertEquals("b1a511134edad636bdd0b84df83e3a5ff2cb90c93a74050ae41d060f87e5e1e5",
                RequestFingerprint.calculate(CommandType.SetResourceStatus, resource, new SetResourceStatusPayload(false, "Mantenimiento", 0)));
    }

    @Test void semanticChangesHaveDifferentHashes() {
        String original = RequestFingerprint.calculate(CommandType.CreateBooking, resource, create("Reunión Prisma"));
        for (String title : new String[] {"reunión Prisma", "Reunión  Prisma", "Otra reunión"}) {
            assertNotEquals(original, RequestFingerprint.calculate(CommandType.CreateBooking, resource, create(title)));
        }
        assertNotEquals(original, RequestFingerprint.calculate(CommandType.CreateBooking, UUID.randomUUID(), create("Reunión Prisma")));
        assertNotEquals(RequestFingerprint.calculate(CommandType.CancelBooking, resource, new CancelBookingPayload(booking, null)),
                RequestFingerprint.calculate(CommandType.CancelBooking, resource, new CancelBookingPayload(booking, "")));
        assertNotEquals(RequestFingerprint.calculate(CommandType.SetResourceStatus, resource, new SetResourceStatusPayload(false, "Motivo", 0)),
                RequestFingerprint.calculate(CommandType.SetResourceStatus, resource, new SetResourceStatusPayload(false, "Motivo", 1)));
    }

    @Test void rejectsFractionalSecondsAndMismatchedType() {
        var fraction = new CreateBookingPayload(OffsetDateTime.parse("2026-10-28T08:00:00.001Z"),
                OffsetDateTime.parse("2026-10-28T09:00:00Z"), "Título");
        assertThrows(IllegalArgumentException.class, () -> RequestFingerprint.calculate(CommandType.CreateBooking, resource, fraction));
        assertThrows(IllegalArgumentException.class, () -> RequestFingerprint.calculate(CommandType.CancelBooking, resource, create("Título")));
    }

    @Test void everyPayloadFieldParticipatesInTheFingerprint() {
        var original = create("Reunión Prisma");
        String originalHash = RequestFingerprint.calculate(CommandType.CreateBooking, resource, original);
        assertNotEquals(originalHash, RequestFingerprint.calculate(CommandType.CreateBooking, resource,
                new CreateBookingPayload(original.startsAt().plusMinutes(15), original.endsAt(), original.title())));
        assertNotEquals(originalHash, RequestFingerprint.calculate(CommandType.CreateBooking, resource,
                new CreateBookingPayload(original.startsAt(), original.endsAt().plusMinutes(15), original.title())));
        String cancelled = RequestFingerprint.calculate(CommandType.CancelBooking, resource, new CancelBookingPayload(booking, "Motivo"));
        assertEquals(cancelled, RequestFingerprint.calculate(CommandType.CancelBooking, resource, new CancelBookingPayload(booking, "  Motivo  ")));
        assertNotEquals(cancelled, RequestFingerprint.calculate(CommandType.CancelBooking, resource, new CancelBookingPayload(UUID.randomUUID(), "Motivo")));
        assertNotEquals(cancelled, RequestFingerprint.calculate(CommandType.CancelBooking, resource, new CancelBookingPayload(booking, "Otro motivo")));
        String status = RequestFingerprint.calculate(CommandType.SetResourceStatus, resource, new SetResourceStatusPayload(false, "Motivo", 0));
        assertNotEquals(status, RequestFingerprint.calculate(CommandType.SetResourceStatus, resource, new SetResourceStatusPayload(true, "Motivo", 0)));
        assertNotEquals(status, RequestFingerprint.calculate(CommandType.SetResourceStatus, resource, new SetResourceStatusPayload(false, "Otro motivo", 0)));
    }

    @Test void escapingAndUuidNormalizationAreStable() {
        var id = UUID.fromString("AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA");
        String json = RequestFingerprint.canonicalJson(CommandType.CreateBooking, id, create("Sala \"A\"\nRuta\\uno"));
        assertTrue(json.contains(id.toString()));
        assertTrue(json.contains("Sala \\\"A\\\"\\nRuta\\\\uno"));
        assertEquals("Título", ((CreateBookingPayload) RequestFingerprint.normalize(create("  Título\t"))).title());
    }
}
