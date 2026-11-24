package com.boot.compick.shopping.recommendation.dto;

public record AiQuoteItem(String categoryLabel, String name, long price, Long productId) {
  public AiQuoteItem(String categoryLabel, String name, long price) {
    this(categoryLabel, name, price, null);
  }
}
