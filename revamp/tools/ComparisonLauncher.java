import com.boot.compick.CompickApplication;
import com.boot.compick.cart.entity.*;
import com.boot.compick.cart.repository.*;
import com.boot.compick.member.entity.*;
import com.boot.compick.member.repository.*;
import com.boot.compick.order.entity.*;
import com.boot.compick.order.repository.*;
import com.boot.compick.payment.entity.*;
import com.boot.compick.payment.repository.*;
import com.boot.compick.payment.service.*;
import com.boot.compick.product.entity.*;
import com.boot.compick.product.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.SpringApplication;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import org.springframework.web.client.RestClient;

/** Isolated H2 fixtures and a controlled payment benchmark. No live external API calls. */
public class ComparisonLauncher {
  static final boolean BENCHMARK = Boolean.getBoolean("compare.benchmark");
  static final AtomicInteger transactionalCalls = new AtomicInteger();

  static class FakeGateway extends TossPaymentService {
    FakeGateway() {
      super("", "", "", RestClient.builder(), new ObjectMapper());
    }

    @Override
    public Map<String, Object> confirm(String key, String number, long amount) {
      if (TransactionSynchronizationManager.isActualTransactionActive())
        transactionalCalls.incrementAndGet();
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
      return Map.of(
          "status",
          "DONE",
          "orderId",
          number,
          "paymentKey",
          key,
          "totalAmount",
          amount,
          "method",
          "CARD");
    }
  }

  record Case(String login, Long member, String number, Long product) {}

  public static void main(String[] args) throws Exception {
    var app = new SpringApplication(CompickApplication.class);
    if (BENCHMARK)
      app.addInitializers(
          c ->
              ((GenericApplicationContext) c)
                  .registerBean(
                      "controlledGateway",
                      TossPaymentService.class,
                      FakeGateway::new,
                      d -> d.setPrimary(true)));
    var context = app.run(args);
    var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
    var members = context.getBean(MemberRepository.class);
    var products = context.getBean(ProductRepository.class);
    var categories = context.getBean(CategoryRepository.class);
    var orders = context.getBean(OrderRepository.class);
    var payments = context.getBean(PaymentRepository.class);
    var carts = context.getBean(CartRepository.class);
    var items = context.getBean(CartProductItemRepository.class);
    var encoder = context.getBean(PasswordEncoder.class);
    var category = categories.save(new CategoryEntity("CPU"));
    var product = new ProductEntity(category, "AMD Ryzen 5 7600", "AMD", "7600");
    product.updateAdmin(
        category, "AMD Ryzen 5 7600", "AMD", "7600", 249000, 100, "", null, "ON_SALE", "{}");
    product = products.save(product);
    final Long shownProduct = product.getProductId();
    tx.execute(
        s -> {
          var member =
              new Member(
                  "preview",
                  "" + encoder.encode("Preview123!"),
                  "김O현",
                  "preview@example.com",
                  "010-0000-0000");
          member.updateAdministration(MemberRole.ADMIN, MemberStatus.ACTIVE);
          member = members.save(member);
          var pending =
              OrderEntity.create(
                  member.getId(),
                  "CP20261124001",
                  249000,
                  3000,
                  "김O현",
                  "010-0000-0000",
                  "00000",
                  "서울특별시 강동구",
                  "",
                  "문 앞에 놓아 주세요.");
          pending.addProductGroup(shownProduct, "AMD Ryzen 5 7600", 249000, 1);
          orders.save(pending);
          var returned =
              OrderEntity.create(
                  member.getId(),
                  "CP20261124002",
                  249000,
                  3000,
                  "김O현",
                  "010-0000-0000",
                  "00000",
                  "서울특별시 강동구",
                  "",
                  "문 앞에 놓아 주세요.");
          returned.addProductGroup(shownProduct, "AMD Ryzen 5 7600", 249000, 1);
          returned.changeStatus(OrderStatus.DELIVERED);
          returned.requestReturn();
          returned.markStockDeducted();
          orders.save(returned);
          var payment =
              PaymentEntity.createReady(
                  returned.getOrderId(), PaymentMethod.TOSS, 252000, "example-only-key");
          payment.approve("EXAMPLE");
          payments.save(payment);
          return null;
        });
    System.out.println("COMPARISON_READY");
    if (!BENCHMARK) return;
    var service = context.getBean(PaymentService.class);
    int index = 0;
    java.util.function.IntFunction<Case> prepare =
        i ->
            tx.execute(
                s -> {
                  String login = "bench" + i;
                  var member =
                      members.save(
                          new Member(
                              login,
                              "unused",
                              "김O현",
                              login + "@example.com",
                              "010-0000-0000"));
                  var p = new ProductEntity(category, "Benchmark " + i, "Test", "Model" + i);
                  p.updateAdmin(
                      category,
                      "Benchmark " + i,
                      "Test",
                      "Model" + i,
                      1000,
                      2,
                      "",
                      null,
                      "ON_SALE",
                      "{}");
                  p = products.save(p);
                  var cart = carts.save(CartEntity.create(member.getId()));
                  items.save(CartProductItemEntity.create(cart, p.getProductId(), 1));
                  var o =
                      OrderEntity.create(
                          member.getId(),
                          "BENCH" + i,
                          1000,
                          0,
                          "김O현",
                          "010-0000-0000",
                          "00000",
                          "서울특별시 강동구",
                          "",
                          "");
                  o.addProductGroup(p.getProductId(), p.getProductName(), 1000, 1);
                  orders.save(o);
                  return new Case(login, member.getId(), o.getOrderNumber(), p.getProductId());
                });
    for (int i = 0; i < 4; i++) {
      var c = prepare.apply(index++);
      service.confirmToss(c.login(), c.number(), "bench-key-" + c.number(), 1000, c.member());
    }
    transactionalCalls.set(0);
    var samples = new ArrayList<Map<String, Object>>();
    var batches = new ArrayList<Double>();
    var pool = Executors.newFixedThreadPool(8);
    try {
      for (int round = 0; round < 5; round++) {
        var cases = new ArrayList<Case>();
        for (int i = 0; i < 8; i++) cases.add(prepare.apply(index++));
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Map<String, Object>>>();
        for (var c : cases)
          futures.add(
              pool.submit(
                  () -> {
                    ready.countDown();
                    start.await();
                    long t = System.nanoTime();
                    service.confirmToss(
                        c.login(), c.number(), "bench-key-" + c.number(), 1000, c.member());
                    return Map.of("order", c.number(), "elapsed_ms", (System.nanoTime() - t) / 1e6);
                  }));
        ready.await();
        long t = System.nanoTime();
        start.countDown();
        for (var f : futures) samples.add(f.get(20, TimeUnit.SECONDS));
        batches.add((System.nanoTime() - t) / 1e6);
        for (var c : cases) {
          if (orders.findByOrderNumber(c.number()).orElseThrow().getOrderStatus()
                  != OrderStatus.PAID
              || products.findById(c.product()).orElseThrow().getStockQuantity() != 1)
            throw new IllegalStateException("Incorrect final order/stock state");
        }
      }
    } finally {
      pool.shutdownNow();
    }
    var data = new LinkedHashMap<String, Object>();
    data.put("label", System.getProperty("compare.label"));
    data.put("gateway_delay_ms", 500);
    data.put("pool_size", 2);
    data.put("parallel_requests", 8);
    data.put("warmup", 4);
    data.put("rounds", 5);
    data.put("samples", samples);
    data.put("batch_elapsed_ms", batches);
    data.put("gateway_calls_inside_db_transaction", transactionalCalls.get());
    data.put("successful_orders", samples.size());
    data.put("verified_stock_rows", samples.size());
    Files.writeString(
        Path.of(System.getProperty("compare.output")),
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data));
    context.close();
  }
}
