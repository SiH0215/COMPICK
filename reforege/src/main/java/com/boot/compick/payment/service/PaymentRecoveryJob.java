package com.boot.compick.payment.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(
    name = "payments.recovery-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PaymentRecoveryJob {
  private final PaymentService service;
  private final PaymentWorkService work;
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(PaymentRecoveryJob.class);

  public PaymentRecoveryJob(PaymentService s, PaymentWorkService w) {
    service = s;
    work = w;
  }

  @Scheduled(fixedDelayString = "${payments.recovery-delay-ms:60000}", initialDelay = 60000)
  public void recover() {
    if (!service.isTossConfigured()) return;
    for (var a : work.recoverable())
      try {
        service.reconcile(a.login(), a.number(), a.member());
      } catch (RuntimeException e) {
        log.warn("결제 복구 대기: order={}", a.number());
      }
  }
}
