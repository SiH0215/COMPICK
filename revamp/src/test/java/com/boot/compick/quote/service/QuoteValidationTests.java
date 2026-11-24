package com.boot.compick.quote.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.boot.compick.product.entity.*;
import com.boot.compick.product.repository.ProductRepository;
import com.boot.compick.shopping.recommendation.dto.AiQuoteItem;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class QuoteValidationTests {
  List<ProductEntity> parts;
  Map<Long, Integer> quantities;

  @BeforeEach
  void prepare() {
    parts = new ArrayList<>();
    quantities = new HashMap<>();
    long id = 1;
    for (var name :
        List.of(
            "CPU", "CPU_COOLER", "MAINBOARD", "RAM", "GPU", "STORAGE", "POWER_SUPPLY", "CASE")) {
      var p = new ProductEntity(new CategoryEntity(name), name, "Test", name);
      p.updateAdmin(p.getCategory(), name, "Test", name, 100, 20, "", null, "ON_SALE", "{}");
      ReflectionTestUtils.setField(p, "productId", id);
      quantities.put(id++, 1);
      parts.add(p);
    }
    set("CPU", "socketType", "AM4");
    set("CPU", "powerConsumption", 65);
    set("MAINBOARD", "socketType", "AM4");
    set("MAINBOARD", "memoryType", "DDR4");
    set("MAINBOARD", "formFactor", "ATX");
    set("MAINBOARD", "specJson", "{\"Memory Slots\":\"4\"}");
    set("RAM", "memoryType", "DDR4");
    set("CASE", "formFactor", "ATX Mid Tower");
    set("CASE", "maxGpuLengthMm", 300);
    set("CASE", "specJson", "{\"Internal 3.5\\\" Bays\":\"2\"}");
    set("GPU", "gpuLengthMm", 200);
    set("GPU", "recommendedPower", 200);
    set("POWER_SUPPLY", "powerCapacityWatt", 600);
  }

  ProductEntity part(String name) {
    return parts.stream()
        .filter(p -> p.getCategory().getCategoryName().equals(name))
        .findFirst()
        .orElseThrow();
  }

  void set(String category, String field, Object value) {
    ReflectionTestUtils.setField(part(category), field, value);
  }

  @Test
  void completeCompatibleQuoteIsAccepted() {
    assertThatCode(() -> QuoteSelectionValidator.validate(parts, quantities))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"socket", "memory", "case", "gpu", "power", "slots", "bays"})
  void incompatiblePartsAreRejected(String kind) {
    switch (kind) {
      case "socket" -> set("CPU", "socketType", "AM5");
      case "memory" -> set("RAM", "memoryType", "DDR5");
      case "case" -> set("CASE", "formFactor", "Mini ITX");
      case "gpu" -> set("GPU", "gpuLengthMm", 400);
      case "power" -> set("POWER_SUPPLY", "powerCapacityWatt", 200);
      case "slots" -> quantities.put(part("RAM").getProductId(), 3);
      case "bays" -> {
        set("STORAGE", "specJson", "{\"Form Factor\":\"3.5\\\"\"}");
        quantities.put(part("STORAGE").getProductId(), 3);
      }
    }
    assertThatThrownBy(() -> QuoteSelectionValidator.validate(parts, quantities))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  }

  @Test
  void missingCategoryRejected() {
    parts.remove(part("CPU_COOLER"));
    assertThatThrownBy(() -> QuoteSelectionValidator.validate(parts, quantities))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void insufficientStockRejected() {
    quantities.put(part("STORAGE").getProductId(), 21);
    assertThatThrownBy(() -> QuoteSelectionValidator.validate(parts, quantities))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void singleSelectQuantityCannotBeIncreased() {
    quantities.put(part("CPU").getProductId(), 2);
    assertThatThrownBy(() -> QuoteSelectionValidator.validate(parts, quantities))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void aiBudgetIsCheckedAgainstCurrentDatabasePrices() {
    var repository = mock(ProductRepository.class);
    when(repository.findAllById(any())).thenReturn(parts);
    var service = new AiQuoteValidationService(repository);
    var items =
        parts.stream()
            .map(p -> new AiQuoteItem("ignored", p.getProductName(), 1, p.getProductId()))
            .toList();
    assertThatThrownBy(() -> service.validate("예산 700원", items))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(service.validate("예산 1만원", items)).allMatch(p -> p.price() == 100);
  }

  @Test
  void aiDuplicateOrUnknownProductsAreRejected() {
    var repository = mock(ProductRepository.class);
    when(repository.findAllById(any())).thenReturn(List.of());
    var service = new AiQuoteValidationService(repository);
    var item = new AiQuoteItem("CPU", "test", 100, 1L);
    assertThatThrownBy(() -> service.validate("", List.of(item, item)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.validate("", List.of(item)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void explicitCurrencyBudgetParsing() {
    assertThat(AiQuoteValidationService.budget("예산 150만원, 메모리 32GB")).isEqualTo(1500000);
    assertThat(AiQuoteValidationService.budget("1,500,000원 이하")).isEqualTo(1500000);
    assertThat(AiQuoteValidationService.budget("32GB 메모리")).isNull();
  }
}
