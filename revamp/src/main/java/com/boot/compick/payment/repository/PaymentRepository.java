package com.boot.compick.payment.repository;

import com.boot.compick.payment.entity.PaymentEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

  Optional<PaymentEntity> findByOrderId(Long orderId);

  Optional<PaymentEntity> findByExternalTransactionId(String externalTransactionId);

  @org.springframework.data.jpa.repository.Query(
      "select p from PaymentEntity p where p.paymentStatus ="
          + " com.boot.compick.payment.entity.PaymentStatus.READY and (p.processingUntil is null or"
          + " p.processingUntil < :now)")
  java.util.List<PaymentEntity> recoverable(
      @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now,
      org.springframework.data.domain.Pageable page);
}
