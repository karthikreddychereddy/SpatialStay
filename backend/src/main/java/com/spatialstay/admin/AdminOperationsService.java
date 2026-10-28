package com.spatialstay.admin;

import com.spatialstay.booking.*;
import com.spatialstay.hotel.*;
import com.spatialstay.payment.*;
import com.spatialstay.refund.*;
import com.spatialstay.user.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.spatialstay.admin.AdminOperationsModels.*;

@Service
public class AdminOperationsService {
    private final HotelRepository hotels; private final UserRepository users; private final BookingRepository bookings;
    private final PaymentAttemptRepository payments; private final RefundRepository refunds;
    private final CapturedPaymentReconciliationCaseRepository reconciliation; private final HotelApplicationRepository applications;
    private final AdminAuditEventRepository audits; private final AdminAuditService auditService; private final CurrentUser currentUser;

    public AdminOperationsService(HotelRepository hotels,UserRepository users,BookingRepository bookings,PaymentAttemptRepository payments,RefundRepository refunds,CapturedPaymentReconciliationCaseRepository reconciliation,HotelApplicationRepository applications,AdminAuditEventRepository audits,AdminAuditService auditService,CurrentUser currentUser){
        this.hotels=hotels;this.users=users;this.bookings=bookings;this.payments=payments;this.refunds=refunds;this.reconciliation=reconciliation;this.applications=applications;this.audits=audits;this.auditService=auditService;this.currentUser=currentUser;
    }

    @Transactional(readOnly=true) public List<HotelSummary> hotels(){return hotels.findAll().stream().sorted(Comparator.comparing(Hotel::getName,String.CASE_INSENSITIVE_ORDER)).map(h->new HotelSummary(h.getId(),h.getSlug(),h.getName(),h.getCity(),h.getState(),h.getCountry(),h.getVerified(),h.getStatus(),h.getRating(),h.getReviewCount(),h.getStartingPrice(),h.getContactEmail(),h.getCreatedAt())).toList();}
    @Transactional public HotelSummary setHotelStatus(Long id,String status){String s=status==null?"":status.trim().toUpperCase();if(!List.of("ACTIVE","SUSPENDED").contains(s))throw new IllegalArgumentException("Hotel status must be ACTIVE or SUSPENDED.");Hotel h=hotels.findById(id).orElseThrow(()->new IllegalArgumentException("Hotel not found."));h.setStatus(s);hotels.save(h);auditService.record("HOTEL_STATUS_CHANGED","HOTEL",id.toString(),"Hotel status changed to "+s+".");return new HotelSummary(h.getId(),h.getSlug(),h.getName(),h.getCity(),h.getState(),h.getCountry(),h.getVerified(),h.getStatus(),h.getRating(),h.getReviewCount(),h.getStartingPrice(),h.getContactEmail(),h.getCreatedAt());}

    @Transactional(readOnly=true) public List<UserSummary> users(){return users.findAll().stream().sorted(Comparator.comparing(User::getCreatedAt).reversed()).map(this::user).toList();}
    @Transactional public UserSummary setUserStatus(UUID id,UserStatus status){if(status==null||!(status==UserStatus.ACTIVE||status==UserStatus.DISABLED))throw new IllegalArgumentException("User status must be ACTIVE or DISABLED.");if(currentUser.publicId().equals(id))throw new IllegalStateException("An administrator cannot disable their own account.");User u=users.findByPublicId(id).orElseThrow(()->new IllegalArgumentException("User not found."));if(u.getRole()==UserRole.ADMIN)throw new IllegalStateException("Admin accounts cannot be changed from this operation.");u.changeStatus(status);users.save(u);auditService.record("USER_STATUS_CHANGED","USER",id.toString(),"User status changed to "+status+".");return user(u);}
    private UserSummary user(User u){return new UserSummary(u.getPublicId(),u.getEmail(),u.getFirstName(),u.getLastName(),u.getRole().name(),u.getStatus().name(),u.isEmailVerified(),u.getCreatedAt(),u.getLastLoginAt());}

    @Transactional(readOnly=true) public List<BookingSummary> bookings(){return bookings.findAll().stream().sorted(Comparator.comparing(Booking::getCreatedAt).reversed()).map(b->new BookingSummary(b.getPublicId(),b.getBookingReference(),b.getUser().getPublicId(),b.getUser().getEmail(),b.getHotel().getId(),b.getHotel().getName(),b.getCheckIn(),b.getCheckOut(),b.getGuestCount(),b.getRoomQuantity(),b.getTotalAmount(),b.getCurrency(),b.getStatus().name(),b.getCreatedAt(),b.getConfirmedAt())).toList();}
    @Transactional(readOnly=true) public List<PaymentSummary> payments(){return payments.findAll().stream().sorted(Comparator.comparing(PaymentAttempt::getCreatedAt).reversed()).map(p->new PaymentSummary(p.getPublicId(),p.getBooking().getPublicId(),p.getBooking().getBookingReference(),p.getUser().getEmail(),p.getProvider().name(),p.getProviderOrderId(),p.getProviderPaymentId(),p.getExpectedAmount(),p.getCurrency(),p.getStatus().name(),p.getCreatedAt(),p.getSucceededAt())).toList();}
    @Transactional(readOnly=true) public List<RefundSummary> refunds(){return refunds.findAll().stream().sorted(Comparator.comparing(Refund::getCreatedAt).reversed()).map(r->new RefundSummary(r.getPublicId(),r.getBooking().getPublicId(),r.getBooking().getBookingReference(),r.getUser().getEmail(),r.getProvider().name(),r.getProviderPaymentId(),r.getProviderRefundId(),r.getRefundAmount(),r.getCurrency(),r.getStatus().name(),r.getFailureReason(),r.getCreatedAt(),r.getSucceededAt())).toList();}
    @Transactional(readOnly=true) public List<ReconciliationSummary> reconciliation(){return reconciliation.findAll().stream().sorted(Comparator.comparing(CapturedPaymentReconciliationCase::getDiscoveredAt).reversed()).map(c->new ReconciliationSummary(c.getId(),c.getPaymentAttemptPublicId(),c.getBookingPublicId(),c.getProvider().name(),c.getProviderOrderId(),c.getProviderPaymentId(),c.getBookingStatusAtDiscovery(),c.getStatus(),c.getDiscoveredAt())).toList();}
    @Transactional(readOnly=true) public List<AuditSummary> audits(){return audits.findAll().stream().sorted(Comparator.comparing(AdminAuditEvent::getCreatedAt).reversed()).map(a->new AuditSummary(a.getId(),a.getAdmin().getPublicId(),a.getAdmin().getEmail(),a.getAction(),a.getTargetType(),a.getTargetId(),a.getDetail(),a.getCreatedAt())).toList();}

    @Transactional(readOnly=true) public Analytics analytics(){List<Booking> bs=bookings.findAll();List<PaymentAttempt> ps=payments.findAll();List<Refund> rs=refunds.findAll();List<User> us=users.findAll();BigDecimal bookingValue=bs.stream().filter(b->b.getStatus()==BookingStatus.CONFIRMED).map(Booking::getTotalAmount).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal paymentValue=ps.stream().filter(p->p.getStatus()==PaymentStatus.SUCCEEDED).map(PaymentAttempt::getExpectedAmount).reduce(BigDecimal.ZERO,BigDecimal::add);BigDecimal refundValue=rs.stream().filter(r->r.getStatus()==RefundStatus.SUCCEEDED).map(Refund::getRefundAmount).reduce(BigDecimal.ZERO,BigDecimal::add);return new Analytics(hotels.count(),us.size(),countRole(us,UserRole.CUSTOMER),countRole(us,UserRole.HOTEL_HEAD),countRole(us,UserRole.HOTEL_STAFF),bs.size(),bs.stream().filter(b->b.getStatus()==BookingStatus.CONFIRMED).count(),bs.stream().filter(b->b.getStatus()==BookingStatus.CANCELLED).count(),ps.size(),ps.stream().filter(p->p.getStatus()==PaymentStatus.SUCCEEDED).count(),rs.size(),rs.stream().filter(r->r.getStatus()==RefundStatus.SUCCEEDED).count(),applications.countByStatus(HotelApplicationStatus.PENDING_APPROVAL),reconciliation.count(),bookingValue,paymentValue,refundValue);}
    private long countRole(List<User> us,UserRole role){return us.stream().filter(u->u.getRole()==role).count();}
}
