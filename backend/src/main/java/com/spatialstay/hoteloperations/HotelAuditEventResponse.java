package com.spatialstay.hoteloperations;

import com.spatialstay.user.UserRole;
import java.time.Instant;
import java.util.UUID;

public record HotelAuditEventResponse(Long id, Long hotelId, UUID actorPublicId, UserRole actorRole,
                                      String action, String resourceType, String resourceId,
                                      String summary, Instant occurredAt) {}
