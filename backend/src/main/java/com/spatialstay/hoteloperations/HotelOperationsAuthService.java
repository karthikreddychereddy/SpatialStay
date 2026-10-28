package com.spatialstay.hoteloperations;

import com.spatialstay.user.CurrentUser;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class HotelOperationsAuthService {

    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final HotelOperationsAccessService accessService;

    public HotelOperationsAuthService(
            CurrentUser currentUser,
            UserRepository userRepository,
            HotelOperationsAccessService accessService
    ) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.accessService = accessService;
    }

    @Transactional(readOnly = true)
    public HotelOperationsLoginResponse currentOperator() {
        User user = userRepository.findByPublicId(currentUser.publicId())
                .orElseThrow(() -> new AccessDeniedException("Hotel operator account was not found."));

        List<HotelUserAssignment> assignments = accessService.requireAndGetAssignments();

        return new HotelOperationsLoginResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                assignments.stream()
                        .map(assignment -> new HotelOperationsLoginResponse.HotelAssignmentSummary(
                                assignment.getHotel().getId(),
                                assignment.getHotel().getSlug(),
                                assignment.getHotel().getName(),
                                assignment.getHotel().getCity(),
                                assignment.getHotel().getCountry()
                        ))
                        .toList()
        );
    }
}
