-- 교육 수료 후 개선 작업본의 기존 Oracle DB에 1회 적용합니다.
ALTER TABLE PAYMENT ADD (processing_until TIMESTAMP);
ALTER TABLE PAYMENT MODIFY (external_transaction_id VARCHAR2(200));
ALTER TABLE ORDERS ADD (return_completed_at TIMESTAMP);
-- 기존 return_requested_at 데이터는 환불 내역과 대조하여 완료 여부를 별도로 이관합니다.
