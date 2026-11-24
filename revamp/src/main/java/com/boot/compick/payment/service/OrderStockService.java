package com.boot.compick.payment.service;

import com.boot.compick.order.entity.*;
import com.boot.compick.product.entity.ProductEntity;
import com.boot.compick.product.repository.ProductRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class OrderStockService {
  private final ProductRepository products;

  public OrderStockService(ProductRepository p) {
    products = p;
  }

  public void reserve(OrderEntity order) {
    if (order.isStockDeducted()) return;
    var quantities = quantities(order);
    var locked = lock(quantities);
    if (locked.size() != quantities.size())
      throw new ResponseStatusException(HttpStatus.CONFLICT, "주문 상품을 찾을 수 없습니다.");
    quantities.forEach(
        (id, n) -> {
          var p = locked.get(id);
          if (!"ON_SALE".equals(p.getSalesStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "판매가 중단된 상품입니다.");
          p.decreaseStock(n);
        });
    order.markStockDeducted();
  }

  public void restore(OrderEntity order) {
    if (!order.isStockDeducted() || order.isStockRestored()) return;
    var quantities = quantities(order);
    var locked = lock(quantities);
    quantities.forEach(
        (id, n) -> {
          var p = locked.get(id);
          if (p != null) p.increaseStock(n);
        });
    order.markStockRestored();
  }

  private Map<Long, ProductEntity> lock(Map<Long, Integer> quantities) {
    Map<Long, ProductEntity> result = new LinkedHashMap<>();
    quantities.keySet().stream()
        .sorted()
        .forEach(id -> products.findByIdForUpdate(id).ifPresent(p -> result.put(id, p)));
    return result;
  }

  private Map<Long, Integer> quantities(OrderEntity order) {
    Map<Long, Integer> result = new TreeMap<>();
    for (var group : order.getGroups())
      for (var item : group.getItems()) {
        int count =
            Math.multiplyExact(
                item.getQuantity(),
                group.getGroupType() == OrderGroupType.QUOTE ? group.getGroupQuantity() : 1);
        if (count < 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "주문 수량이 올바르지 않습니다.");
        result.merge(item.getProductId(), count, Math::addExact);
      }
    if (result.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "주문 상품이 없습니다.");
    return result;
  }
}
