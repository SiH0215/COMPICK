package com.boot.compick.order.repository;

import com.boot.compick.order.entity.OrderEntity;
import com.boot.compick.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

  // Lock only the order row; load collections after acquiring it to avoid outer-join locking.
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from OrderEntity o where o.orderNumber = :orderNumber")
  Optional<OrderEntity> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

  /*
   * groups와 groups.items를 한 EntityGraph에서 같이 즉시 로딩하면 Hibernate가
   * MultipleBagFetchException을 던진다(List 컬렉션 두 단계를 동시에 fetch join 불가).
   * groups만 즉시 로딩하고 items는 (호출부가 같은 트랜잭션 안에서만 접근하므로) 지연 로딩에 맡긴다.
   */
  @EntityGraph(attributePaths = "groups")
  Optional<OrderEntity> findByOrderNumber(String orderNumber);

  @EntityGraph(attributePaths = "groups")
  Optional<OrderEntity> findByOrderNumberAndMemberId(String orderNumber, Long memberId);

  List<OrderEntity> findByMemberIdOrderByOrderedAtDesc(Long memberId);

  List<OrderEntity> findByMemberIdAndOrderStatusOrderByOrderedAtDesc(
      Long memberId, OrderStatus orderStatus);

  long countByOrderedAtBetween(java.time.LocalDateTime start, java.time.LocalDateTime end);

  @EntityGraph(attributePaths = "groups")
  List<OrderEntity> findAllByOrderByOrderedAtDesc();

  @EntityGraph(attributePaths = "groups")
  List<OrderEntity> findTop5ByOrderByOrderedAtDesc();
}
