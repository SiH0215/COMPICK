package com.boot.compick.quote.service;

import com.boot.compick.product.repository.ProductRepository;
import com.boot.compick.shopping.recommendation.dto.AiQuoteItem;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiQuoteValidationService {
  private final ProductRepository repository;

  public AiQuoteValidationService(ProductRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<AiQuoteItem> validate(String requirements, List<AiQuoteItem> items) {
    if (items == null || items.isEmpty() || items.stream().anyMatch(i -> i.productId() == null))
      throw new IllegalArgumentException("추천 상품을 확인할 수 없습니다. 다시 추천해 주세요.");
    var quantities = new LinkedHashMap<Long, Integer>();
    for (var i : items)
      if (quantities.put(i.productId(), 1) != null)
        throw new IllegalArgumentException("추천 상품이 중복되었습니다.");
    var products = repository.findAllById(quantities.keySet());
    if (products.size() != items.size()) throw new IllegalArgumentException("추천 상품이 판매 목록에 없습니다.");
    QuoteSelectionValidator.validate(products, quantities);
    var budget = budget(requirements);
    long total = products.stream().mapToLong(p -> p.getPrice()).sum();
    if (budget != null && total > budget)
      throw new IllegalArgumentException("추천 금액이 입력한 최대 예산을 초과했습니다. 예산을 조정하거나 다시 추천해 주세요.");
    var byId = new HashMap<Long, com.boot.compick.product.entity.ProductEntity>();
    products.forEach(p -> byId.put(p.getProductId(), p));
    return items.stream()
        .map(
            i -> {
              var p = byId.get(i.productId());
              return new AiQuoteItem(
                  p.getCategory().getCategoryName(),
                  p.getProductName(),
                  p.getPrice(),
                  p.getProductId());
            })
        .toList();
  }

  static Long budget(String text) {
    if (text == null) return null;
    var m = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(만\\s*원|원)").matcher(text);
    if (!m.find()) return null;
    try {
      return new java.math.BigDecimal(m.group(1).replace(",", ""))
          .multiply(new java.math.BigDecimal(m.group(2).startsWith("만") ? 10000 : 1))
          .longValueExact();
    } catch (ArithmeticException e) {
      throw new IllegalArgumentException("예산 금액을 확인해 주세요.");
    }
  }
}
