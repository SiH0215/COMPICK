package com.boot.compick.payment.service;

import com.boot.compick.cart.service.CartService;
import com.boot.compick.order.entity.*;
import com.boot.compick.order.repository.OrderRepository;
import com.boot.compick.payment.entity.*;
import com.boot.compick.payment.repository.PaymentRepository;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class PaymentWorkService {
  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final com.boot.compick.member.repository.MemberRepository members;
  private final OrderStockService stock;
  private final CartService cartService;

  public PaymentWorkService(
      OrderRepository o,
      PaymentRepository p,
      OrderStockService stock,
      CartService c,
      com.boot.compick.member.repository.MemberRepository members) {
    this.members = members;
    orderRepository = o;
    paymentRepository = p;
    this.stock = stock;
    cartService = c;
  }

  public record Attempt(
      String login,
      String number,
      Long member,
      String key,
      long amount,
      boolean existing,
      boolean done) {}

  public Attempt begin(String login, String number, String key, long amount, Long member) {
    var order = owned(number, member);
    if (order.getFinalAmount() != amount) throw conflict("결제 금액이 주문 금액과 다릅니다.");
    if (key == null || key.isBlank() || key.length() > 200) throw conflict("결제 식별값이 올바르지 않습니다.");
    var found = paymentRepository.findByOrderId(order.getOrderId());
    if (found.isPresent() && !key.equals(found.get().getExternalTransactionId()))
      throw conflict("이 주문의 결제 식별값이 일치하지 않습니다.");
    if (found.isPresent() && found.get().getPaymentStatus() == PaymentStatus.APPROVED)
      return new Attempt(login, number, member, key, amount, true, true);
    if (order.getOrderStatus() != OrderStatus.PAYMENT_PENDING)
      throw conflict("결제를 진행할 수 없는 주문입니다.");
    if (found.isPresent() && found.get().getPaymentStatus() != PaymentStatus.READY)
      throw conflict("종료된 결제입니다. 새 주문으로 진행해 주세요.");
    if (found.isPresent() && found.get().processing())
      throw conflict("결제를 확인 중입니다. 잠시 후 결제 상태 확인을 눌러 주세요.");
    boolean existing = found.isPresent();
    var payment =
        found.orElseGet(
            () -> {
              stock.reserve(order);
              return paymentRepository.save(
                  PaymentEntity.createReady(order.getOrderId(), PaymentMethod.TOSS, amount, key));
            });
    payment.claim();
    return new Attempt(login, number, member, key, amount, existing, false);
  }

  @Transactional(readOnly = true)
  public java.util.List<Attempt> recoverable() {
    return paymentRepository
        .recoverable(
            java.time.LocalDateTime.now(), org.springframework.data.domain.PageRequest.of(0, 20))
        .stream()
        .map(
            p -> {
              var o = orderRepository.findById(p.getOrderId()).orElseThrow();
              var m = members.findById(o.getMemberId()).orElseThrow();
              return new Attempt(
                  m.getLoginId(),
                  o.getOrderNumber(),
                  m.getId(),
                  p.getExternalTransactionId(),
                  p.getPaymentAmount(),
                  true,
                  false);
            })
        .toList();
  }

  public Attempt stored(String login, String number, Long member) {
    var o = owned(number, member);
    var p =
        paymentRepository
            .findByOrderId(o.getOrderId())
            .orElseThrow(() -> conflict("확인할 결제가 없습니다."));
    return new Attempt(
        login,
        number,
        member,
        p.getExternalTransactionId(),
        p.getPaymentAmount(),
        true,
        p.getPaymentStatus() == PaymentStatus.APPROVED);
  }

  public void finish(Attempt a, Map<String, Object> remote) {
    var o = owned(a.number(), a.member());
    var p = paymentRepository.findByOrderId(o.getOrderId()).orElseThrow();
    if (p.getPaymentStatus() == PaymentStatus.APPROVED) return;
    if (p.getPaymentStatus() != PaymentStatus.READY
        || !a.key().equals(p.getExternalTransactionId())) throw conflict("결제 상태가 변경되었습니다.");
    p.approve(String.valueOf(remote.getOrDefault("method", "TOSS")));
    o.markPaid();
    clearOrderedCartItems(a.login(), o);
  }

  public void fail(Attempt a) {
    var o = owned(a.number(), a.member());
    var p = paymentRepository.findByOrderId(o.getOrderId()).orElseThrow();
    if (p.getPaymentStatus() == PaymentStatus.READY) {
      stock.restore(o);
      o.resetStockReservation();
      p.fail();
    }
  }

  public void release(Attempt a) {
    var o = owned(a.number(), a.member());
    paymentRepository
        .findByOrderId(o.getOrderId())
        .filter(p -> p.getPaymentStatus() == PaymentStatus.READY)
        .ifPresent(PaymentEntity::release);
  }

  private OrderEntity owned(String n, Long m) {
    return orderRepository
        .findByOrderNumberForUpdate(n)
        .filter(o -> m.equals(o.getMemberId()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "주문을 찾을 수 없습니다."));
  }

  private static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  private void clearOrderedCartItems(String loginId, OrderEntity order) {
    java.util.Set<Long> products = new java.util.TreeSet<>();
    java.util.Set<Long> quotes = new java.util.TreeSet<>();
    for (OrderGroupEntity group : order.getGroups()) {
      if (group.getGroupType() == OrderGroupType.PRODUCT) {
        group.getItems().forEach(item -> products.add(item.getProductId()));
      } else if (group.getSourceQuoteId() != null) {
        quotes.add(group.getSourceQuoteId());
      }
    }
    cartService.removePurchasedItems(loginId, products, quotes);
  }
}
