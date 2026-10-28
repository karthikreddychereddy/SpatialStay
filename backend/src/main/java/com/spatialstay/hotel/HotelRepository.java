package com.spatialstay.hotel;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

public interface HotelRepository
        extends JpaRepository<Hotel, Long>,
        JpaSpecificationExecutor<Hotel> {

    /*
     * Step 12A.8:
     * Hotel amenities are an ElementCollection and intentionally remain LAZY.
     * Public/API mapping happens after HotelService returns, so the queries that
     * feed those mappers must fetch amenities while the persistence context is
     * still open. EntityGraph avoids changing the collection globally to EAGER.
     */
    @EntityGraph(attributePaths = "amenities")
    Optional<Hotel> findBySlug(String slug);

    @Override
    @EntityGraph(attributePaths = "amenities")
    List<Hotel> findAll(Specification<Hotel> spec, Sort sort);

    boolean existsBySlug(String slug);
}
