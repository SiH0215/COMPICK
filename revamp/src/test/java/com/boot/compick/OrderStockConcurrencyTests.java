package com.boot.compick;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.boot.compick.cart.entity.*;
import com.boot.compick.cart.repository.*;
import com.boot.compick.member.entity.*;
import com.boot.compick.member.repository.*;
import com.boot.compick.order.dto.CreateOrderRequest;
import com.boot.compick.order.entity.*;
import com.boot.compick.order.repository.OrderRepository;
import com.boot.compick.order.service.OrderService;
import com.boot.compick.payment.repository.PaymentRepository;
import com.boot.compick.payment.service.*;
import com.boot.compick.product.entity.*;
import com.boot.compick.product.repository.*;
import com.boot.compick.shopping.recommendation.service.GeminiRecommendationService;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:concurrency;MODE=Oracle;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
      "spring.datasource.hikari.maximum-pool-size=16",
      "gemini.api-key=",
      "toss.payments.client-key=",
      "toss.payments.secret-key=",
      "GOOGLE_CLIENT_ID=",
      "GOOGLE_CLIENT_SECRET="
    })
class OrderStockConcurrencyTests {
  @Autowired OrderService orders;
  @Autowired PaymentService payments;
  @Autowired OrderRepository orderRepository;
  @Autowired ProductRepository products;
  @Autowired CategoryRepository categories;
  @Autowired MemberRepository members;
  @Autowired AddressRepository addresses;
  @Autowired CartRepository carts;
  @Autowired CartProductItemRepository cartItems;
  @Autowired PaymentRepository paymentRepository;
  @Autowired PlatformTransactionManager transactionManager;
  @MockitoBean TossPaymentService toss;
  @MockitoBean GeminiRecommendationService gemini;
  @org.springframework.test.context.bean.override.mockito.MockitoSpyBean PaymentWorkService work;

  record Buyer(String login, Long id, Long addressId) {}

  TransactionTemplate tx;

  @BeforeEach
  void prepare() {
    tx = new TransactionTemplate(transactionManager);
    when(toss.confirm(anyString(), anyString(), anyLong()))
        .thenAnswer(
            a -> {
              assertThat(
                      org.springframework.transaction.support.TransactionSynchronizationManager
                          .isActualTransactionActive())
                  .isFalse();
              return Map.of(
                  "method",
                  "CARD",
                  "status",
                  "DONE",
                  "paymentKey",
                  a.getArgument(0),
                  "orderId",
                  a.getArgument(1),
                  "totalAmount",
                  a.getArgument(2, Long.class));
            });
  }

  @Test
  void concurrentOrderCreationHasDistinctOrderNumbers() throws Exception {
    Long product = product(100);
    List<Buyer> buyers = new ArrayList<>();
    for (int i = 0; i < 12; i++) buyers.add(buyer(product));
    var numbers =
        concurrent(
            12,
            i ->
                orders
                    .createPendingOrder(
                        buyers.get(i).login(),
                        new CreateOrderRequest(buyers.get(i).addressId(), ""))
                    .orderNumber());
    assertThat(new HashSet<>(numbers)).hasSize(12);
    assertThat(numbers).allMatch(n -> n.matches("CP\\d{8}[a-f0-9]{32}"));
    for (String number : numbers) assertThat(orderRepository.findByOrderNumber(number)).isPresent();
  }

  @Test
  void competingPaymentsCannotOversell() throws Exception {
    Long product = product(3);
    Buyer buyer = buyer(product);
    List<String> numbers = new ArrayList<>();
    for (int i = 0; i < 8; i++) numbers.add(pending(buyer, List.of(product)));
    var results = concurrent(8, i -> approve(buyer, numbers.get(i), 100));
    assertThat(results).filteredOn(Boolean::booleanValue).hasSize(3);
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isZero();
    verify(toss, times(3)).confirm(anyString(), anyString(), anyLong());
    assertThat(
            numbers.stream()
                .filter(
                    n ->
                        orderRepository.findByOrderNumber(n).orElseThrow().getOrderStatus()
                            == OrderStatus.PAID)
                .count())
        .isEqualTo(3);
  }

  @Test
  void repeatedConfirmationOfOneOrderDeductsStockOnce() throws Exception {
    Long product = product(5);
    Buyer buyer = buyer(product);
    String number = pending(buyer, List.of(product));
    var results = concurrent(8, i -> approve(buyer, number, 100));
    assertThat(results).filteredOn(Boolean::booleanValue).isNotEmpty();
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(4);
    verify(toss, times(1)).confirm(anyString(), anyString(), anyLong());
  }

  @Test
  void ambiguousApprovalFailureKeepsDurableReservation() {
    Long product = product(2);
    Buyer buyer = buyer(product);
    String number = pending(buyer, List.of(product));
    when(toss.confirm(anyString(), eq(number), anyLong()))
        .thenThrow(new IllegalStateException("test gateway failure"));
    assertThatThrownBy(
            () -> payments.confirmToss(buyer.login(), number, "test-key", 100, buyer.id()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(1);
    var order = orderRepository.findByOrderNumber(number).orElseThrow();
    assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.PAYMENT_PENDING);
    assertThat(order.isStockDeducted()).isTrue();
    assertThat(paymentRepository.findByOrderId(order.getOrderId()))
        .hasValueSatisfying(
            p ->
                assertThat(p.getPaymentStatus())
                    .isEqualTo(com.boot.compick.payment.entity.PaymentStatus.READY));
  }

  @Test
  void reversedProductSelectionUsesConsistentLockOrder() throws Exception {
    Long a = product(2), b = product(2);
    Buyer buyer = buyer(a);
    var numbers = List.of(pending(buyer, List.of(a, b)), pending(buyer, List.of(b, a)));
    assertThat(concurrent(2, i -> approve(buyer, numbers.get(i), 200))).containsOnly(true);
    assertThat(products.findById(a).orElseThrow().getStockQuantity()).isZero();
    assertThat(products.findById(b).orElseThrow().getStockQuantity()).isZero();
  }

  @Test
  void concurrentCancellationAndPurchasePreserveStock() throws Exception {
    Long product = product(2);
    Buyer buyer = buyer(product);
    String paid = pending(buyer, List.of(product));
    assertThat(approve(buyer, paid, 100)).isTrue();
    String next = pending(buyer, List.of(product));
    var results =
        concurrent(
            2,
            i -> {
              if (i == 0) {
                orders.requestCancel(buyer.login(), paid);
                return true;
              }
              return approve(buyer, next, 100);
            });
    assertThat(results).containsOnly(true);
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(1);
    assertThat(orderRepository.findByOrderNumber(paid).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.CANCELLED);
  }

  @Test
  void approvedGatewayResultRecoversAfterDatabaseFinalizationFailure() {
    Long product = product(2);
    Buyer buyer = buyer(product);
    String number = pending(buyer, List.of(product));
    var first = new java.util.concurrent.atomic.AtomicBoolean(true);
    doAnswer(
            a -> {
              if (first.getAndSet(false))
                throw new IllegalStateException("simulated commit failure");
              return a.callRealMethod();
            })
        .when(work)
        .finish(any(), anyMap());
    assertThatThrownBy(
            () -> payments.confirmToss(buyer.login(), number, "test-" + number, 100, buyer.id()))
        .isInstanceOf(IllegalStateException.class);
    when(toss.lookup("test-" + number))
        .thenReturn(
            Map.of(
                "method",
                "CARD",
                "status",
                "DONE",
                "paymentKey",
                "test-" + number,
                "orderId",
                number,
                "totalAmount",
                100));
    payments.reconcile(buyer.login(), number, buyer.id());
    assertThat(orderRepository.findByOrderNumber(number).orElseThrow().getOrderStatus())
        .isEqualTo(OrderStatus.PAID);
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(1);
    verify(toss, times(1)).confirm(anyString(), anyString(), anyLong());
  }

  @Test
  void rejectedPaymentReleasesReservationOnReconciliation() {
    Long product = product(2);
    Buyer buyer = buyer(product);
    String number = pending(buyer, List.of(product));
    when(toss.confirm(anyString(), eq(number), anyLong()))
        .thenThrow(new IllegalStateException("network failure"));
    assertThatThrownBy(
            () -> payments.confirmToss(buyer.login(), number, "test-" + number, 100, buyer.id()))
        .isInstanceOf(IllegalStateException.class);
    when(toss.lookup("test-" + number)).thenReturn(Map.of("status", "ABORTED"));
    assertThatThrownBy(() -> payments.reconcile(buyer.login(), number, buyer.id()))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(2);
    assertThat(orderRepository.findByOrderNumber(number).orElseThrow().isStockDeducted()).isFalse();
  }

  @Test
  void amountMismatchNeverCallsGatewayOrReservesStock() {
    Long product = product(2);
    Buyer buyer = buyer(product);
    String number = pending(buyer, List.of(product));
    assertThatThrownBy(
            () -> payments.confirmToss(buyer.login(), number, "test-" + number, 1, buyer.id()))
        .isInstanceOf(ResponseStatusException.class);
    verify(toss, never()).confirm(anyString(), anyString(), anyLong());
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(2);
  }

  @Test
  void anotherMemberCannotAccessPaymentRecovery() {
    Long product = product(2);
    Buyer owner = buyer(product), other = buyer(product);
    String number = pending(owner, List.of(product));
    assertThatThrownBy(() -> payments.reconcile(other.login(), number, other.id()))
        .isInstanceOf(ResponseStatusException.class);
    verifyNoInteractions(toss);
  }

  @Test
  void returnRequestDoesNotRefundUntilAdminApprovalAndRefundsOnlyOnce() {
    Long product = product(2);
    Buyer buyer = buyer(product);
    String number = pending(buyer, List.of(product));
    approve(buyer, number, 100);
    tx.executeWithoutResult(
        s ->
            orderRepository
                .findByOrderNumberForUpdate(number)
                .orElseThrow()
                .changeStatus(OrderStatus.DELIVERED));
    orders.requestReturn(buyer.login(), number);
    assertThat(orderRepository.findByOrderNumber(number).orElseThrow().isReturnCompleted())
        .isFalse();
    verify(toss, never()).cancel(anyString(), anyString());
    when(toss.balanceAmount("test-" + number)).thenReturn(100L);
    orders.approveReturn(number);
    orders.approveReturn(number);
    verify(toss, times(1)).cancel(eq("test-" + number), anyString());
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(2);
    assertThat(orderRepository.findByOrderNumber(number).orElseThrow().isReturnCompleted())
        .isTrue();
  }

  @Test
  void pendingApprovalCannotBeCancelledAndExposeReservedStock() {
    Long product = product(2);
    Buyer buyer = buyer(product);
    String number = pending(buyer, List.of(product));
    when(toss.confirm(anyString(), eq(number), anyLong()))
        .thenThrow(new IllegalStateException("timeout"));
    assertThatThrownBy(
            () -> payments.confirmToss(buyer.login(), number, "test-" + number, 100, buyer.id()))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> orders.requestCancel(buyer.login(), number))
        .isInstanceOf(ResponseStatusException.class);
    assertThat(products.findById(product).orElseThrow().getStockQuantity()).isEqualTo(1);
  }

  private boolean approve(Buyer buyer, String number, long amount) {
    try {
      payments.confirmToss(buyer.login(), number, "test-" + number, amount, buyer.id());
      return true;
    } catch (ResponseStatusException e) {
      assertThat(e.getStatusCode().value()).isEqualTo(409);
      return false;
    }
  }

  private Long product(int stock) {
    return tx.execute(
        s -> {
          var category =
              categories.save(
                  new CategoryEntity("T" + UUID.randomUUID().toString().substring(0, 8)));
          var product = new ProductEntity(category, "Test product", "Test", "Model");
          product.updateAdmin(
              category, "Test product", "Test", "Model", 100, stock, "", null, "ON_SALE", "{}");
          return products.save(product).getProductId();
        });
  }

  private Buyer buyer(Long product) {
    return tx.execute(
        s -> {
          String login = "u" + UUID.randomUUID().toString().replace("-", "");
          var member =
              members.save(
                  new Member(login, "unused", "김O현", login + "@example.com", "010-0000-0000"));
          var address =
              addresses.save(
                  new Address(
                      member,
                      "Home",
                      "김O현",
                      "010-0000-0000",
                      "00000",
                      "서울특별시 강동구",
                      "",
                      true));
          var cart = carts.save(CartEntity.create(member.getId()));
          cartItems.save(CartProductItemEntity.create(cart, product, 1));
          return new Buyer(login, member.getId(), address.getId());
        });
  }

  private String pending(Buyer buyer, List<Long> ids) {
    return tx.execute(
        s -> {
          var order =
              OrderEntity.create(
                  buyer.id(),
                  "TEST" + UUID.randomUUID().toString().replace("-", ""),
                  ids.size() * 100L,
                  0,
                  "김O현",
                  "010-0000-0000",
                  "00000",
                  "서울특별시 강동구",
                  "",
                  "");
          ids.forEach(id -> order.addProductGroup(id, "Test product", 100, 1));
          return orderRepository.save(order).getOrderNumber();
        });
  }

  private <T> List<T> concurrent(int count, IntFunction<T> action) throws Exception {
    var pool = Executors.newFixedThreadPool(count);
    var ready = new CountDownLatch(count);
    var start = new CountDownLatch(1);
    List<Future<T>> futures = new ArrayList<>();
    try {
      for (int i = 0; i < count; i++) {
        final int index = i;
        futures.add(
            pool.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("start timeout");
                  return action.apply(index);
                }));
      }
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<T> result = new ArrayList<>();
      for (var f : futures) result.add(f.get(30, TimeUnit.SECONDS));
      return result;
    } finally {
      start.countDown();
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
