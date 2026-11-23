# 페이지별 설명

[프로젝트 소개](../README.md) · [주요 기능](FEATURES.md)

컨트롤러 경로와 반환 템플릿을 기준으로 화면을 정리하였습니다. POST 경로는 입력 검증·오류 처리 후 같은 화면을 다시 표시하는 경우도 포함하였습니다.

## 사용자 쇼핑 화면

| 요청 | 경로 | 기능 | 화면 코드 |
|---|---|---|---|
| GET | `/cart` | 상품·견적 장바구니 | [cart/index](../src/main/resources/templates/cart/index.html) |
| GET | `/` | 홈과 쇼핑 진입 메뉴 | [home/index](../src/main/resources/templates/home/index.html) |
| GET | `/products` | 상품 목록·조건별 필터·상세 정보 진입 | [shopping/products](../src/main/resources/templates/shopping/products.html) |
| GET | `/orders/new` | 주문서 | [order/new](../src/main/resources/templates/order/new.html) |
| GET | `/orders` | 내 주문 목록 | [order/list](../src/main/resources/templates/order/list.html) |
| GET | `/orders/{orderNumber}` | 주문 상세 | [order/detail](../src/main/resources/templates/order/detail.html) |
| GET | `/payments/success` | 결제 승인 성공 결과 | [payment/success](../src/main/resources/templates/payment/success.html) |
| GET | `/payments/success` | 결제 실패 안내 | [payment/fail](../src/main/resources/templates/payment/fail.html) |
| GET | `/payments/fail` | 결제 실패 안내 | [payment/fail](../src/main/resources/templates/payment/fail.html) |
| GET | `/quotes/new` | 부품을 직접 선택하는 견적 구성 | [shopping/quote-new](../src/main/resources/templates/shopping/quote-new.html) |
| GET | `/preset` | 추천 견적 목록 | [shopping/preset-list](../src/main/resources/templates/shopping/preset-list.html) |
| GET | `/preset/{quoteId}` | 추천 견적 상세 | [shopping/preset-detail](../src/main/resources/templates/shopping/preset-detail.html) |
| GET | `/ai-quotes` | 요구사항 입력·AI 추천 결과·합계 금액 | [shopping/recommendation/ai-quote](../src/main/resources/templates/shopping/recommendation/ai-quote.html) |
| POST | `/ai-quotes` | 요구사항 입력·AI 추천 결과·합계 금액 | [shopping/recommendation/ai-quote](../src/main/resources/templates/shopping/recommendation/ai-quote.html) |

## 회원·계정·배송지 화면

| 요청 | 경로 | 기능 | 화면 코드 |
|---|---|---|---|
| GET | `/mypage/addresses` | 배송지 목록 | [member/address-list](../src/main/resources/templates/member/address-list.html) |
| GET | `/mypage/addresses/new` | 배송지 등록·수정 | [member/address-form](../src/main/resources/templates/member/address-form.html) |
| GET | `/mypage/addresses/{addressId}/edit` | 배송지 등록·수정 | [member/address-form](../src/main/resources/templates/member/address-form.html) |
| POST | `/mypage/addresses` | 배송지 등록·수정 | [member/address-form](../src/main/resources/templates/member/address-form.html) |
| POST | `/mypage/addresses/{addressId}` | 배송지 등록·수정 | [member/address-form](../src/main/resources/templates/member/address-form.html) |
| GET | `/login` | 로그인 | [member/login](../src/main/resources/templates/member/login.html) |
| GET | `/members/signup` | member/join | [member/join](../src/main/resources/templates/member/join.html) |
| POST | `/members/signup` | member/join | [member/join](../src/main/resources/templates/member/join.html) |
| GET | `/members/social-password` | 소셜 계정 인증 정보 설정 | [member/social-credentials](../src/main/resources/templates/member/social-credentials.html) |
| POST | `/members/social-password` | 소셜 계정 인증 정보 설정 | [member/social-credentials](../src/main/resources/templates/member/social-credentials.html) |
| GET | `/mypage` | 마이페이지 | [member/mypage](../src/main/resources/templates/member/mypage.html) |
| GET | `/mypage/profile` | 회원 정보 수정 | [member/profile](../src/main/resources/templates/member/profile.html) |
| POST | `/mypage/profile` | 회원 정보 수정 | [member/profile](../src/main/resources/templates/member/profile.html) |
| POST | `/mypage/password` | 회원 정보 수정 | [member/profile](../src/main/resources/templates/member/profile.html) |
| GET | `/members/find-id` | 아이디 찾기 요청 | [member/find-id](../src/main/resources/templates/member/find-id.html) |
| POST | `/members/find-id/verify` | 아이디 찾기 인증 | [member/find-id-verify](../src/main/resources/templates/member/find-id-verify.html) |
| GET | `/members/password-reset` | 비밀번호 재설정 요청 | [member/password-reset](../src/main/resources/templates/member/password-reset.html) |
| POST | `/members/password-reset/verify` | 비밀번호 재설정 인증 | [member/password-reset-verify](../src/main/resources/templates/member/password-reset-verify.html) |
| POST | `/members/password-reset/change` | 새 비밀번호 설정 | [member/password-reset-change](../src/main/resources/templates/member/password-reset-change.html) |

## 관리자 화면

| 요청 | 경로 | 기능 | 화면 코드 |
|---|---|---|---|
| GET | `/admin` | 관리자 대시보드 | [admin/dashboard](../src/main/resources/templates/admin/dashboard.html) |
| GET | `/admin/members` | 회원 목록 | [admin/member-list](../src/main/resources/templates/admin/member-list.html) |
| GET | `/admin/members/{id}` | 회원 상세·수정 | [admin/member-detail](../src/main/resources/templates/admin/member-detail.html) |
| GET | `/admin/products` | 상품 관리 | [admin/product-form](../src/main/resources/templates/admin/product-form.html) |
| GET | `/admin/presets` | 추천 견적 관리 | [admin/preset-list](../src/main/resources/templates/admin/preset-list.html) |
| GET | `/admin/presets/new` | 추천 견적 등록·수정 | [admin/preset-form](../src/main/resources/templates/admin/preset-form.html) |
| GET | `/admin/presets/{id}/edit` | 추천 견적 등록·수정 | [admin/preset-form](../src/main/resources/templates/admin/preset-form.html) |
| GET | `/admin/orders` | 주문 관리 | [admin/order-list](../src/main/resources/templates/admin/order-list.html) |
| GET | `/admin/orders/{orderNumber}` | 주문 상세·상태 변경 | [admin/order-detail](../src/main/resources/templates/admin/order-detail.html) |
| GET | `/admin/ai-logs` | AI 추천 로그 | [admin/ai-log-list](../src/main/resources/templates/admin/ai-log-list.html) |

## 정책 화면

| 요청 | 경로 | 기능 | 화면 코드 |
|---|---|---|---|
| GET | `/privacy-policy` | 개인정보 처리방침 | [privacy-policy](../src/main/resources/templates/privacy-policy.html) |
| GET | `/terms-of-service` | 이용약관 | [terms-of-service](../src/main/resources/templates/terms-of-service.html) |

## 공통 화면과 별도 확인 대상

헤더·푸터·마이페이지 사이드바·관리자 조각 템플릿은 개별 방문 페이지와 구분하였습니다. 아래는 위 경로 목록에 직접 연결되지 않은 템플릿이며, 파일 존재만으로 별도의 사용 화면이라고 판단하지 않았습니다.

- [admin/fragments.html](../src/main/resources/templates/admin/fragments.html)
- [fragments/footer.html](../src/main/resources/templates/fragments/footer.html)
- [fragments/header.html](../src/main/resources/templates/fragments/header.html)
- [fragments/mypage-sidebar.html](../src/main/resources/templates/fragments/mypage-sidebar.html)
- [member/find-id-result.html](../src/main/resources/templates/member/find-id-result.html)
- [member/signup.html](../src/main/resources/templates/member/signup.html)
