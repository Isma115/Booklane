import { describe, expect, it } from 'vitest';
import type { Session, CreateBookingRequest, CancelBookingRequest, SetResourceStatusRequest, Accepted, OperationResult, ApiError, Resource, Availability, Booking, AuditEntry, ConsumerStatus, DemoStatus, ResourcePage, BookingPage, AuditEntryPage } from './api.models';

const session = {
  "authenticated": true,
  "user": {
    "id": "20000000-0000-4000-8000-000000000001",
    "username": "usuario-demo",
    "role": "USER"
  }
} satisfies Session;

const createBookingRequest = {
  "commandId": "30000000-0000-4000-8000-000000000001",
  "resourceId": "10000000-0000-4000-8000-000000000001",
  "startsAt": "2026-10-28T08:00:00Z",
  "endsAt": "2026-10-28T09:00:00Z",
  "title": "Reunión Prisma"
} satisfies CreateBookingRequest;

const cancelBookingRequest = {
  "commandId": "30000000-0000-4000-8000-000000000001",
  "resourceId": "10000000-0000-4000-8000-000000000001",
  "reason": null
} satisfies CancelBookingRequest;

const setResourceStatusRequest = {
  "commandId": "30000000-0000-4000-8000-000000000001",
  "active": false,
  "reason": "Mantenimiento",
  "expectedVersion": 0
} satisfies SetResourceStatusRequest;

const accepted = {
  "commandId": "30000000-0000-4000-8000-000000000001",
  "requestHash": "3bd360463c43aa7e2f23b157e6d280740b6ebbe49dec7d4ef5a04857d2ccece0",
  "transportStatus": "ACCEPTED",
  "operationUrl": "/api/operations/30000000-0000-4000-8000-000000000001"
} satisfies Accepted;

const operationResult = {
  "commandId": "30000000-0000-4000-8000-000000000001",
  "requestHash": "3bd360463c43aa7e2f23b157e6d280740b6ebbe49dec7d4ef5a04857d2ccece0",
  "outcome": "SUCCEEDED",
  "reasonCode": "CONFIRMED",
  "resourceId": "10000000-0000-4000-8000-000000000001",
  "bookingId": "60000000-0000-4000-8000-000000000001",
  "resourceVersion": 1,
  "processedAt": "2026-10-27T10:00:01Z",
  "canonicalRequest": {
    "type": "CreateBooking",
    "resourceId": "10000000-0000-4000-8000-000000000001",
    "payload": {
      "startsAt": "2026-10-28T08:00:00Z",
      "endsAt": "2026-10-28T09:00:00Z",
      "title": "Reunión Prisma"
    }
  }
} satisfies OperationResult;

const apiError = {
  "code": "OPERATION_NOT_OBSERVED",
  "message": "No hay resultado disponible",
  "traceId": "50000000-0000-4000-8000-000000000001"
} satisfies ApiError;

const resource = {
  "id": "10000000-0000-4000-8000-000000000001",
  "code": "SALA-ATLAS",
  "name": "Sala Atlas",
  "type": "ROOM",
  "capacity": 8,
  "description": "Sala con pantalla",
  "active": true,
  "resourceVersion": 0
} satisfies Resource;

const availability = {
  "resourceId": "10000000-0000-4000-8000-000000000001",
  "active": true,
  "resourceVersion": 0,
  "timezone": "Europe/Madrid",
  "days": [
    {
      "date": "2026-10-28",
      "opensAt": "2026-10-28T07:00:00Z",
      "closesAt": "2026-10-28T21:00:00Z",
      "anchors": [
        "2026-10-28T07:00:00Z",
        "2026-10-28T07:15:00Z"
      ]
    }
  ],
  "occupied": []
} satisfies Availability;

const booking = {
  "id": "60000000-0000-4000-8000-000000000001",
  "resourceId": "10000000-0000-4000-8000-000000000001",
  "ownerId": "20000000-0000-4000-8000-000000000001",
  "title": "Reunión Prisma",
  "startsAt": "2026-10-28T08:00:00Z",
  "endsAt": "2026-10-28T09:00:00Z",
  "status": "CONFIRMED",
  "createdAt": "2026-10-27T10:00:01Z",
  "cancelledAt": null,
  "version": 0
} satisfies Booking;

const auditEntry = {
  "id": "70000000-0000-4000-8000-000000000001",
  "actorId": "20000000-0000-4000-8000-000000000001",
  "commandId": "30000000-0000-4000-8000-000000000001",
  "requestHash": "3bd360463c43aa7e2f23b157e6d280740b6ebbe49dec7d4ef5a04857d2ccece0",
  "action": "CreateBooking",
  "resourceId": "10000000-0000-4000-8000-000000000001",
  "bookingId": "60000000-0000-4000-8000-000000000001",
  "outcome": "SUCCEEDED",
  "reasonCode": "CONFIRMED",
  "reason": null,
  "occurredAt": "2026-10-27T10:00:01Z"
} satisfies AuditEntry;

const consumerStatus = {
  "state": "PAUSED",
  "observedAt": "2026-10-27T10:00:01Z"
} satisfies ConsumerStatus;

const demoStatus = {
  "api": "UP",
  "booking": "UP",
  "kafka": "UP",
  "database": "UP",
  "consumer": "RUNNING",
  "observedAt": "2026-10-27T10:00:01Z"
} satisfies DemoStatus;

const resourcePage = {
  "items": [
    {
      "id": "10000000-0000-4000-8000-000000000001",
      "code": "SALA-ATLAS",
      "name": "Sala Atlas",
      "type": "ROOM",
      "capacity": 8,
      "description": "Sala con pantalla",
      "active": true,
      "resourceVersion": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
} satisfies ResourcePage;

const bookingPage = {
  "items": [
    {
      "id": "60000000-0000-4000-8000-000000000001",
      "resourceId": "10000000-0000-4000-8000-000000000001",
      "ownerId": "20000000-0000-4000-8000-000000000001",
      "title": "Reunión Prisma",
      "startsAt": "2026-10-28T08:00:00Z",
      "endsAt": "2026-10-28T09:00:00Z",
      "status": "CONFIRMED",
      "createdAt": "2026-10-27T10:00:01Z",
      "cancelledAt": null,
      "version": 0
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
} satisfies BookingPage;

const auditEntryPage = {
  "items": [
    {
      "id": "70000000-0000-4000-8000-000000000001",
      "actorId": "20000000-0000-4000-8000-000000000001",
      "commandId": "30000000-0000-4000-8000-000000000001",
      "requestHash": "3bd360463c43aa7e2f23b157e6d280740b6ebbe49dec7d4ef5a04857d2ccece0",
      "action": "CreateBooking",
      "resourceId": "10000000-0000-4000-8000-000000000001",
      "bookingId": "60000000-0000-4000-8000-000000000001",
      "outcome": "SUCCEEDED",
      "reasonCode": "CONFIRMED",
      "reason": null,
      "occurredAt": "2026-10-27T10:00:01Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
} satisfies AuditEntryPage;

describe('Ejemplos públicos de contrato', () => {
  it('distingue aceptación de resultado y disponibilidad anónima de detalle privado', () => {
    expect(accepted.transportStatus).toBe('ACCEPTED');
    expect('bookingId' in accepted).toBe(false);
    expect(operationResult.outcome).toBe('SUCCEEDED');
    expect(operationResult.canonicalRequest.resourceId).toBe(operationResult.resourceId);
    expect(apiError.code).toBe('OPERATION_NOT_OBSERVED');
    expect(availability.timezone).toBe('Europe/Madrid');
    expect(Object.keys(availability)).not.toContain('ownerId');
    expect(booking.ownerId).toBe(session.user.id);
  });
});
