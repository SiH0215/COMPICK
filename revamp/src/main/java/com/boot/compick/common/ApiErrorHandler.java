package com.boot.compick.common;

import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(annotations = RestController.class)
public class ApiErrorHandler {
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<?> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("message", e.getReason() == null ? "요청을 처리하지 못했습니다." : e.getReason()));
  }

  @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
  public ResponseEntity<?> invalid(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "message",
                e instanceof IllegalArgumentException ? e.getMessage() : "입력한 내용을 확인해 주세요."));
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<?> failure(RuntimeException e) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(Map.of("message", "요청을 완료하지 못했습니다. 입력한 내용을 유지한 채 다시 시도해 주세요."));
  }
}
