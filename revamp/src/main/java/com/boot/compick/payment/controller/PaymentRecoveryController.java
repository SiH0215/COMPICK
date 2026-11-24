package com.boot.compick.payment.controller;

import com.boot.compick.member.repository.MemberRepository;
import com.boot.compick.payment.service.PaymentService;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class PaymentRecoveryController {
  private final PaymentService payments;
  private final MemberRepository members;

  public PaymentRecoveryController(PaymentService p, MemberRepository m) {
    payments = p;
    members = m;
  }

  @PostMapping("/{number}/payment-reconcile")
  public void reconcile(@PathVariable String number, Principal principal) {
    var member =
        members
            .findByLoginId(principal.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    payments.reconcile(principal.getName(), number, member.getId());
  }
}
