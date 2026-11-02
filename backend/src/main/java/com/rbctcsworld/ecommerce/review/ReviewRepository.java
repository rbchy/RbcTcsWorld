package com.rbctcsworld.ecommerce.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    /** Owner check built into the query: another customer's review id behaves as "not found" (IDOR). */
    Optional<Review> findByIdAndUserId(Long id, Long userId);

    List<Review> findByProductIdAndStatus(Long productId, String status);

    List<Review> findByUserIdOrderByIdDesc(Long userId);

    List<Review> findByUserId(Long userId);

    List<Review> findByStatusOrderByIdDesc(String status);

    List<Review> findAllByOrderByIdDesc();

    /** [rating, count] pairs of the PUBLISHED reviews of one product. */
    @Query("select r.rating, count(r) from Review r where r.productId = :productId and r.status = 'PUBLISHED' group by r.rating")
    List<Object[]> ratingHistogram(@Param("productId") Long productId);
}
