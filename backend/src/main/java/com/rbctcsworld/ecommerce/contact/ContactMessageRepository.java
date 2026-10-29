package com.rbctcsworld.ecommerce.contact;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    long countByEmailAndCreatedAtAfter(String email, LocalDateTime after);

    long countByCreatedAtAfter(LocalDateTime after);

    boolean existsByTicket(String ticket);

    List<ContactMessage> findAllByOrderByCreatedAtDescIdDesc(Pageable page);

    List<ContactMessage> findByStatusOrderByCreatedAtDescIdDesc(String status, Pageable page);
}
