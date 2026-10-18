package com.rbctcsworld.ecommerce.address;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    /** The default first, then the newest. */
    List<CustomerAddress> findByUserIdOrderByIsDefaultDescCreatedAtDescIdDesc(Long userId);

    Optional<CustomerAddress> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
