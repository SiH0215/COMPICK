package com.boot.compick.shopping.recommendation.service;

import com.boot.compick.product.repository.ProductRepository;
import com.boot.compick.shopping.recommendation.dto.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseAiCatalog {
  private final ProductRepository repository;

  public DatabaseAiCatalog(ProductRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public AiProductCatalog build() {
    var map = new LinkedHashMap<String, AiQuoteItem>();
    var csv = new StringBuilder("ai_product_id,category,name,price,specs\n");
    repository.findAll().stream()
        .filter(p -> "ON_SALE".equals(p.getSalesStatus()) && p.getStockQuantity() > 0)
        .sorted(Comparator.comparing(p -> p.getProductId()))
        .forEach(
            p -> {
              var id = "DB-" + p.getProductId();
              var category = p.getCategory().getCategoryName();
              map.put(
                  id,
                  new AiQuoteItem(category, p.getProductName(), p.getPrice(), p.getProductId()));
              csv.append(id)
                  .append(',')
                  .append(category)
                  .append(',')
                  .append(escape(p.getProductName()))
                  .append(',')
                  .append(p.getPrice())
                  .append(',')
                  .append(escape(p.getSpecJson()))
                  .append('\n');
            });
    return new AiProductCatalog(csv.toString().getBytes(StandardCharsets.UTF_8), Map.copyOf(map));
  }

  private static String escape(String s) {
    return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
  }
}
