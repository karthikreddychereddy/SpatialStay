package com.spatialstay.hoteloperations;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.user.CurrentUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class HotelAuditService {
    private final HotelAuditEventRepository repository;
    private final HotelOperationsAccessService accessService;
    private final CurrentUser currentUser;

    public HotelAuditService(HotelAuditEventRepository repository, HotelOperationsAccessService accessService, CurrentUser currentUser) {
        this.repository = repository; this.accessService = accessService; this.currentUser = currentUser;
    }

    public void record(Hotel hotel, String action, String resourceType, String resourceId, String summary) {
        repository.save(new HotelAuditEvent(hotel, currentUser.publicId(), currentUser.role(), action,
                resourceType, resourceId, safe(summary), Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<HotelAuditEventResponse> list(Long requestedHotelId, Integer requestedLimit) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        int limit = requestedLimit == null ? 100 : Math.max(1, Math.min(requestedLimit, 200));
        return repository.findByHotelIdOrderByOccurredAtDescIdDesc(hotel.getId(), PageRequest.of(0, limit))
                .stream().map(e -> new HotelAuditEventResponse(e.getId(), hotel.getId(), e.getActorPublicId(),
                        e.getActorRole(), e.getAction(), e.getResourceType(), e.getResourceId(), e.getSummary(), e.getOccurredAt()))
                .toList();
    }

    private String safe(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }
}
