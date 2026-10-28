// Generated from contracts/http/public-api.yaml; checked by scripts/verify-contracts.py.
export type User = { "id": string; "username": string; "role": "USER" | "ADMIN" };

export type Session = { "authenticated": false } | { "authenticated": true; "user": User };

export type LoginRequest = { "username": string; "password": string };

export type CreateBookingRequest = { "commandId": string; "resourceId": string; "startsAt": string; "endsAt": string; "title": string };

export type CancelBookingRequest = { "commandId": string; "resourceId": string; "reason"?: string | null };

export type SetResourceStatusRequest = { "commandId": string; "active": boolean; "reason": string; "expectedVersion": number };

export type Accepted = { "commandId": string; "requestHash": string; "transportStatus": "ACCEPTED"; "operationUrl": string };

export type CanonicalRequest = { "type": "CreateBooking"; "resourceId": string; "payload": { "startsAt": string; "endsAt": string; "title": string } } | { "type": "CancelBooking"; "resourceId": string; "payload": { "bookingId": string; "reason"?: string | null } } | { "type": "SetResourceStatus"; "resourceId": string; "payload": { "active": boolean; "reason": string; "expectedVersion": number } };

export type OperationResult = { "commandId": string; "requestHash": string; "outcome": "SUCCEEDED" | "REJECTED"; "reasonCode": string; "resourceId": string; "bookingId": string | null; "resourceVersion": number | null; "processedAt": string; "canonicalRequest": CanonicalRequest; "alreadyCancelled"?: boolean };

export type ApiError = { "code": string; "message": string; "fieldErrors"?: Record<string, Array<string>>; "traceId": string; "commandId"?: string; "requestHash"?: string };

export type Resource = { "id": string; "code": string; "name": string; "type": "ROOM" | "EQUIPMENT"; "capacity": number | null; "description": string; "active": boolean; "resourceVersion": number; "imageUrl"?: string | null };

export type OccupiedInterval = { "startsAt": string; "endsAt": string };

export type AvailabilityDay = { "date": string; "opensAt": string; "closesAt": string; "anchors": Array<string> };

export type Availability = { "resourceId": string; "active": boolean; "resourceVersion": number; "timezone": "Europe/Madrid"; "days": Array<AvailabilityDay>; "occupied": Array<OccupiedInterval> };

export type Booking = { "id": string; "resourceId": string; "ownerId": string; "title": string; "startsAt": string; "endsAt": string; "status": "CONFIRMED" | "CANCELLED"; "createdAt": string; "cancelledAt": string | null; "version": number };

export type AuditEntry = { "id": string; "actorId": string; "commandId": string; "requestHash": string; "action": "CreateBooking" | "CancelBooking" | "SetResourceStatus" | "IDEMPOTENCY_CONFLICT"; "resourceId": string; "bookingId": string | null; "outcome": "SUCCEEDED" | "REJECTED"; "reasonCode": string; "reason": string | null; "occurredAt": string };

export type ConsumerStatus = { "state": "RUNNING" | "PAUSED" | "UNKNOWN"; "observedAt": string };

export type DemoStatus = { "api": "UP" | "DOWN" | "UNKNOWN"; "booking": "UP" | "DOWN" | "UNKNOWN"; "kafka": "UP" | "DOWN" | "UNKNOWN"; "database": "UP" | "DOWN" | "UNKNOWN"; "consumer": "RUNNING" | "PAUSED" | "UNKNOWN"; "observedAt": string };

export type CommandCompleted = { "schemaVersion": 1; "type": "CommandCompleted"; "eventId": string; "commandId": string; "actorId": string; "requestHash": string; "outcome": "SUCCEEDED" | "REJECTED"; "reasonCode": string; "bookingId": string | null; "resourceId": string; "resourceVersion": number | null; "occurredAt": string; "traceId": string };

export type AvailabilityChanged = { "schemaVersion": 1; "type": "ResourceAvailabilityChanged"; "eventId": string; "resourceId": string; "resourceVersion": number; "occurredAt": string };

export type ResourcePage = { "items": Array<Resource>; "page": number; "size": number; "totalElements": number };

export type BookingPage = { "items": Array<Booking>; "page": number; "size": number; "totalElements": number };

export type AuditEntryPage = { "items": Array<AuditEntry>; "page": number; "size": number; "totalElements": number };
