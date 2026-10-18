package com.rbctcsworld.ecommerce.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPaymentMethodRepository extends JpaRepository<SavedPaymentMethod, Long> {

    List<SavedPaymentMethod> findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(Long userId);

    Optional<SavedPaymentMethod> findByIdAndUserId(Long id, Long userId);
}
