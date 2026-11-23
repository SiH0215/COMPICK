# 주요 기능

[프로젝트 소개](../README.md) · [페이지별 설명](PAGES.md)

## 1. 상품 탐색

상품 API에서 카테고리, 브랜드, 최저·최고 가격, 저장장치 유형과 `spec_` 접두사의 사양 조건을 받아 필터링하도록 구현하였습니다. 인기순·가격 오름차순·가격 내림차순 정렬과 페이지 조회를 제공하였습니다. 카테고리별 필터 후보와 상품 상세 정보는 별도 API로 조회하도록 구성하였습니다.

근거: [ProductApiController](../src/main/java/com/boot/compick/product/controller/ProductApiController.java)

## 2. 직접 견적과 추천 견적

사용자가 부품을 선택하는 직접 견적 화면과 미리 구성된 추천 견적의 목록·상세 화면을 구현하였습니다. 견적 검증에서는 8개 카테고리별 필수 선택을 확인하고, RAM·저장장치는 다중 선택을 허용하였습니다. 나머지 카테고리는 하나씩 선택하도록 제한하였으며, 메인보드의 메모리 슬롯 정보가 있을 때 RAM 상품 수량을 검증하였습니다.

이 검증만으로 CPU 소켓·전력·케이스 크기 등 모든 부품 호환성을 보장한다고 기술하지 않았습니다.

근거: [QuotePageController](../src/main/java/com/boot/compick/quote/controller/QuotePageController.java), [QuoteSelectionValidator](../src/main/java/com/boot/compick/quote/service/QuoteSelectionValidator.java)

## 3. AI 견적

사용자가 입력한 요구사항을 Gemini에 전달하고, 추천 결과의 상품 식별값을 상품 정보로 변환하여 부품 목록과 합계 금액을 표시하도록 구현하였습니다. 서버 시작 시 추천용 상품 CSV를 업로드하는 처리와 시스템 프롬프트 로딩 코드를 구성하였습니다.

빈 응답·상품 식별값 누락·응답 해석 실패를 오류로 처리하였으며, 컨트롤러에서 오류 안내를 화면에 전달하도록 구현하였습니다. 로그인한 사용자는 추천 결과를 견적으로 저장하고 장바구니 연계에 필요한 견적 ID를 받도록 구성하였습니다. 견적 저장 실패는 추천 요청 실패와 구분하여 안내하도록 처리하였습니다.

근거: [AI 견적 컨트롤러](../src/main/java/com/boot/compick/shopping/recommendation/controller/AiRecommendationController.java), [GeminiRecommendationService](../src/main/java/com/boot/compick/shopping/recommendation/service/GeminiRecommendationService.java), [추천 프롬프트](../src/main/resources/shopping/ai/system-prompt.txt)

## 4. 장바구니와 주문

개별 상품과 견적을 장바구니에서 관리하도록 구현하였습니다. 상품 수량·선택 상태 변경, 상품·견적 삭제와 선택 항목 조회 기능을 구성하였습니다. 상품을 담거나 수량을 변경할 때 구매 가능 여부를 검사하도록 처리하였습니다.

주문서·주문 목록·주문 상세 화면을 제공하고, 주문 생성 및 취소·반품 요청 API를 구현하였습니다. 주문·장바구니·배송지 API에는 로그인 권한을 요구하도록 구성하였습니다.

근거: [CartService](../src/main/java/com/boot/compick/cart/service/CartService.java), [OrderApiController](../src/main/java/com/boot/compick/order/controller/OrderApiController.java)

## 5. 결제 결과

Toss Payments 성공 콜백에서 결제 키·주문 번호·금액을 받아 현재 회원을 확인하고 결제 승인 서비스에 전달하도록 구현하였습니다. 승인 결과에 따라 성공 또는 실패 화면을 표시하고, 실패 코드와 안내 메시지를 전달하도록 구성하였습니다.

실제 결제 승인·환불의 정상 동작이나 운영 결제 가능 여부는 이번 문서 작업에서 검증하지 않았습니다.

근거: [PaymentController](../src/main/java/com/boot/compick/payment/controller/PaymentController.java)

## 6. 회원과 권한

일반 회원가입·로그인, 이메일 인증, 아이디 찾기, 비밀번호 재설정과 회원 정보·배송지 관리 화면을 구현하였습니다. OAuth2/OIDC 로그인과 소셜 계정의 추가 인증 정보 설정 경로를 구성하였습니다. 외부 로그인은 클라이언트 등록 설정이 존재하는 경우 활성화하도록 처리하였습니다.

관리자 경로는 ADMIN 권한으로 제한하고, 로그인하지 않은 사용자가 인증이 필요한 API를 호출하면 401 응답을 반환하도록 구성하였습니다. 브라우저 페이지 요청은 로그인 화면으로 이동하도록 처리하였습니다.

근거: [SecurityConfig](../src/main/java/com/boot/compick/config/SecurityConfig.java)

## 7. 관리자 화면

대시보드, 회원 목록·상세, 상품 관리, 추천 견적 등록·수정·삭제, 주문 목록·상세와 상태 변경, AI 추천 로그 조회 화면을 구현하였습니다. 각 화면의 경로는 페이지별 설명에 정리하였습니다.

근거: [AdminController](../src/main/java/com/boot/compick/admin/AdminController.java)
