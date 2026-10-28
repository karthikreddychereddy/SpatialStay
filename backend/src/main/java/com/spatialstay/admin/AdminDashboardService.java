package com.spatialstay.admin;

import com.spatialstay.booking.BookingRepository;
import com.spatialstay.hotel.HotelRepository;
import com.spatialstay.payment.CapturedPaymentReconciliationCaseRepository;
import com.spatialstay.payment.PaymentAttemptRepository;
import com.spatialstay.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDashboardService {
    private final HotelRepository hotelRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final CapturedPaymentReconciliationCaseRepository reconciliationRepository;
    private final HotelApplicationRepository applicationRepository;

    public AdminDashboardService(HotelRepository hotelRepository, UserRepository userRepository, BookingRepository bookingRepository, PaymentAttemptRepository paymentAttemptRepository, CapturedPaymentReconciliationCaseRepository reconciliationRepository, HotelApplicationRepository applicationRepository) {
        this.hotelRepository = hotelRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.paymentAttemptRepository = paymentAttemptRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional(readOnly = true)
    public AdminDashboardResponse get() {
        return new AdminDashboardResponse(
                hotelRepository.count(), userRepository.count(), bookingRepository.count(), paymentAttemptRepository.count(),
                applicationRepository.countByStatus(HotelApplicationStatus.PENDING_APPROVAL), reconciliationRepository.count());
    }
}
