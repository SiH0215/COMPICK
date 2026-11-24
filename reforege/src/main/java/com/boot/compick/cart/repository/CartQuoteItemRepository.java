package com.boot.compick.cart.repository;

import com.boot.compick.cart.entity.CartQuoteItemEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartQuoteItemRepository extends JpaRepository<CartQuoteItemEntity, Long> {

  Optional<CartQuoteItemEntity> findByCartCartIdAndQuoteId(Long cartId, Long quoteId);

  List<CartQuoteItemEntity> findByCartCartIdOrderByCartQuoteItemIdDesc(Long cartId);

  List<CartQuoteItemEntity> findByCartCartIdAndSelectedOrderByCartQuoteItemIdDesc(
      Long cartId, String selected);

  @Modifying
  @Query(
      "delete from CartQuoteItemEntity item where item.cart.cartId = :cartId and item.quoteId ="
          + " :itemId")
  int deletePurchasedItem(@Param("cartId") Long cartId, @Param("itemId") Long itemId);
}
