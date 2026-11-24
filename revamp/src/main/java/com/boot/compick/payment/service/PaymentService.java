package com.boot.compick.payment.service;

import com.boot.compick.cart.service.CartService;
import com.boot.compick.order.entity.OrderEntity;
import com.boot.compick.order.repository.OrderRepository;
import com.boot.compick.payment.entity.PaymentEntity;
import com.boot.compick.payment.entity.PaymentStatus;
import com.boot.compick.payment.repository.PaymentRepository;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {

  private final OrderRepository orderRepository;
  private final PaymentRepository paymentRepository;
  private final TossPaymentService tossPaymentService;
  private final CartService cartService;
  private final OrderStockService stock;
  private final PaymentWorkService work;

  public PaymentService(
      OrderRepository orderRepository,
      PaymentRepository paymentRepository,
      TossPaymentService tossPaymentService,
      CartService cartService,
      OrderStockService stock,
      PaymentWorkService work) {
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
    this.tossPaymentService = tossPaymentService;
    this.cartService = cartService;
    this.stock = stock;
    this.work = work;
  }

  public boolean isTossConfigured() {
    return tossPaymentService.isConfigured();
  }

  public String tossClientKey() {
    return tossPaymentService.clientKey();
  }

  public void confirmToss(String loginId, String number, String key, long amount, Long memberId) {
    var attempt = work.begin(loginId, number, key, amount, memberId);
    if (attempt.done()) return;
    try {
      Map<String, Object> remote;
      if (attempt.existing()) {
        remote = tossPaymentService.lookup(key);
        String status = String.valueOf(remote.get("status"));
        if (java.util.Set.of("ABORTED", "EXPIRED", "CANCELED").contains(status)) {
          work.fail(attempt);
          throw new ResponseStatusException(HttpStatus.CONFLICT, "결제가 종료되었습니다. 새 주문으로 진행해 주세요.");
        }
        if (java.util.Set.of("IN_PROGRESS", "READY").contains(status))
          remote = tossPaymentService.confirm(key, number, amount);
      } else remote = tossPaymentService.confirm(key, number, amount);
      if (!"DONE".equals(remote.get("status"))
          || !number.equals(remote.get("orderId"))
          || !key.equals(remote.get("paymentKey"))
          || !(remote.get("totalAmount") instanceof Number total)
          || total.longValue() != amount)
        throw new ResponseStatusException(
            HttpStatus.CONFLICT, "결제 결과를 확인 중입니다. 잠시 후 결제 상태 확인을 눌러 주세요.");
      work.finish(attempt, remote);
    } catch (RuntimeException error) {
      // Keep the persisted reservation on ambiguous network/commit failures.
      try {
        work.release(attempt);
      } catch (RuntimeException releaseError) {
        error.addSuppressed(releaseError);
      }
      throw error;
    }
  }

  public void reconcile(String login, String number, Long member) {
    var a = work.stored(login, number, member);
    if (!a.done()) confirmToss(login, number, a.key(), a.amount(), member);
  }

  @Transactional
  public void cancelForOrder(OrderEntity order) {
    PaymentEntity payment = paymentRepository.findByOrderId(order.getOrderId()).orElse(null);
    if (payment == null || payment.getPaymentStatus() != PaymentStatus.APPROVED) {
      return;
    }
    if (tossPaymentService.balanceAmount(payment.getExternalTransactionId()) > 0)
      tossPaymentService.cancel(payment.getExternalTransactionId(), "고객 요청에 의한 주문 취소");
    payment.cancel();
    stock.restore(order);
  }

  @Transactional
  public void refundForOrder(OrderEntity order) {
    var payment =
        paymentRepository
            .findByOrderId(order.getOrderId())
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.CONFLICT, "환불할 결제 정보가 없습니다."));
    if (payment.getPaymentStatus() == PaymentStatus.CANCELLED && order.isStockRestored()) return;
    if (payment.getPaymentStatus() != PaymentStatus.APPROVED)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "환불할 수 없는 결제입니다.");
    if (tossPaymentService.balanceAmount(payment.getExternalTransactionId()) > 0)
      tossPaymentService.cancel(payment.getExternalTransactionId(), "관리자 승인 반품 전액 환불");
    payment.cancel();
    stock.restore(order);
  }
}
