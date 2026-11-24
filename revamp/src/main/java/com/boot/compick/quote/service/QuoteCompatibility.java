package com.boot.compick.quote.service;

import com.boot.compick.product.SpecJsonSupport;
import com.boot.compick.product.entity.ProductEntity;
import java.util.*;

public final class QuoteCompatibility {
  private QuoteCompatibility() {}

  public static List<String> issues(List<ProductEntity> products, Map<Long, Integer> quantities) {
    List<String> issues = new ArrayList<>();
    Map<String, List<ProductEntity>> groups = new HashMap<>();
    for (var p : products)
      groups.computeIfAbsent(p.getCategory().getCategoryName(), k -> new ArrayList<>()).add(p);
    var cpu = first(groups, "CPU");
    var board = first(groups, "MAINBOARD");
    var gpu = first(groups, "GPU");
    var pcCase = first(groups, "CASE");
    var psu = first(groups, "POWER_SUPPLY");
    if (cpu != null && board != null && different(cpu.getSocketType(), board.getSocketType()))
      issues.add("CPU와 메인보드 소켓이 일치하지 않습니다.");
    if (board != null)
      for (var ram : groups.getOrDefault("RAM", List.of()))
        if (different(ram.getMemoryType(), board.getMemoryType()))
          issues.add("RAM과 메인보드 메모리 규격이 일치하지 않습니다.");
    if (board != null
        && pcCase != null
        && rank(board.getFormFactor()) > 0
        && rank(pcCase.getFormFactor()) > 0
        && rank(board.getFormFactor()) > rank(pcCase.getFormFactor()))
      issues.add("케이스에 메인보드를 장착할 수 없습니다.");
    if (gpu != null
        && pcCase != null
        && gpu.getGpuLengthMm() != null
        && pcCase.getMaxGpuLengthMm() != null
        && gpu.getGpuLengthMm() > pcCase.getMaxGpuLengthMm())
      issues.add("그래픽카드가 케이스의 최대 장착 길이를 초과합니다.");
    if (psu != null && psu.getPowerCapacityWatt() != null) {
      int power =
          (cpu == null || cpu.getPowerConsumption() == null ? 0 : cpu.getPowerConsumption())
              + (gpu == null || gpu.getRecommendedPower() == null ? 0 : gpu.getRecommendedPower());
      if (Math.ceil(power * 1.2) > psu.getPowerCapacityWatt())
        issues.add("파워 용량이 권장 소비전력보다 부족합니다.");
    }
    if (board != null) {
      var slots = SpecJsonSupport.readInt(board.getSpecJson(), "Memory Slots");
      int used =
          groups.getOrDefault("RAM", List.of()).stream()
              .mapToInt(p -> quantities.getOrDefault(p.getProductId(), 1) * 2)
              .sum();
      if (slots != null && used > slots) issues.add("RAM 수량이 메인보드 슬롯 수를 초과합니다.");
    }
    if (pcCase != null) {
      var bays = SpecJsonSupport.readInt(pcCase.getSpecJson(), "Internal 3.5\" Bays");
      int used =
          groups.getOrDefault("STORAGE", List.of()).stream()
              .filter(
                  p ->
                      SpecJsonSupport.readAll(p.getSpecJson())
                          .getOrDefault("Form Factor", "")
                          .startsWith("3.5"))
              .mapToInt(p -> quantities.getOrDefault(p.getProductId(), 1))
              .sum();
      if (bays != null && used > bays) issues.add("HDD 수량이 케이스 베이 수를 초과합니다.");
    }
    return List.copyOf(issues);
  }

  private static boolean different(String a, String b) {
    return a != null
        && !a.isBlank()
        && b != null
        && !b.isBlank()
        && !a.strip().equalsIgnoreCase(b.strip());
  }

  private static ProductEntity first(Map<String, List<ProductEntity>> g, String key) {
    return g.getOrDefault(key, List.of()).stream().findFirst().orElse(null);
  }

  private static int rank(String s) {
    if (s == null) return 0;
    s = s.toUpperCase(Locale.ROOT);
    if (s.contains("EATX") || s.contains("E-ATX")) return 5;
    if (s.contains("MICRO") || s.contains("MATX") || s.contains("M-ATX")) return 3;
    if (s.contains("ITX")) return 1;
    if (s.contains("DTX")) return 2;
    if (s.contains("ATX")) return 4;
    return 0;
  }
}
