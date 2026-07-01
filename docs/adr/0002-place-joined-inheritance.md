# ADR 0002: Place 상속 전략 — JOINED

## 상태 (Status)

Accepted (2026-07-01)

## 배경

주차장(`parking`)과 코스(`course`)는 공통 속성을 다수 공유한다: 좌표, 난이도, 추천도, 찜 개수, 지번/도로명 주소, 코멘트, 지역. 또한 찜·지도 반경검색은 두 유형을 함께 다뤄야 한다. 공통 속성을 중복 없이 표현하고, 두 유형을 하나로 조회할 방법이 필요하다.

## 결정

**공통 슈퍼클래스 `place`를 두고 `parking`·`course`를 JOINED 상속으로 둔다.**

- `place`(공통) + `parking`/`course`(서브)가 `place_id`를 공유 PK로 사용(서브 PK = 부모 FK).
- 구분자 `place.place_type`(PARKING/COURSE).
- **찜(`favorite`)은 `place` 단일 FK로 통합** — 주차장·코스 모두 place라 찜 테이블 하나로 커버.

## 결과

### 긍정적

- 공통 속성 중복 제거, 스키마 일관성.
- 찜·반경검색을 place 단위로 일원화(테이블 하나·쿼리 단순).

### 부정적

- 서브 조회 시 `place`와 조인 필요(약간의 비용).
- 서브타입 분기 로직 필요.

### 중립적

- `place_type` 구분자 컬럼과 JPA `@Inheritance(JOINED)` 매핑 필요.

## 검토한 대안

### 대안 1 — 단일 테이블(Single Table)

`place` 하나에 주차장/코스 컬럼을 모두 nullable로. 조인은 없지만 유형별 컬럼이 대량 nullable이 되어 제약·가독성이 나빠져 선택하지 않음.

### 대안 2 — 독립 테이블

`parking`·`course`를 완전 분리. 공통 속성이 양쪽에 중복되고 찜·검색을 두 번 구현해야 해 선택하지 않음.

## 참조

- [docs/erd.md](../erd.md)
- [ADR 0004](0004-member-soft-delete.md) (찜·리뷰 참조 무결성)
