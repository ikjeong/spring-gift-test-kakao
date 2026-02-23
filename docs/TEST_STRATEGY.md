# 테스트 전략 문서

## 1. 검증할 행위 목록

### 선택 기준

- **API로 노출된 기능만 대상으로 한다.** 서비스 레이어에만 존재하는 기능(위시리스트, 옵션 관리, 회원 관리)은 사용자가 접근할 수 없으므로 제외했다.
- **성공/실패 시나리오 모두 중요하다.** 정상 동작 검증뿐 아니라 예외 상황에서의 안전한 실패도 핵심 비즈니스 요구사항이다.
- **단순 조회는 별도 시나리오로 분리하지 않는다.** 생성 시나리오에서 조회 API를 통해 함께 검증하므로, 조회만을 위한 테스트는 불필요하다.

### 검증 대상 시나리오

|도메인 | 시나리오 | 테스트 클래스 |
|---|---|---|
| 카테고리 | 카테고리를 생성하면 목록 조회 시 조회된다| CategoryAcceptanceTest |
| 상품 | 상품을 생성하면 목록 조회 시 조회된다| ProductAcceptanceTest |
| 상품 | 존재하지 않는 카테고리로 상품을 생성하면 실패한다 | ProductAcceptanceTest |
| 선물 | 선물하기가 정상적으로 처리되면 옵션 재고가 차감된다 | GiftAcceptanceTest |
| 선물 | 재고보다 많은 수량을 선물하면 실패하고 재고는 변경되지 않는다 | GiftAcceptanceTest |
| 선물 | 존재하지 않는 옵션으로 선물하면 실패한다 | GiftAcceptanceTest |
| 선물 | 존재하지 않는 발신자로 선물하면 실패한다 | GiftAcceptanceTest |

---

## 2. 테스트 데이터 전략

### 원칙

1. **매 테스트마다 DB를 초기화한다.** `DatabaseCleaner`를 `@BeforeEach`에서 실행하여 모든 테이블을 TRUNCATE하고 테스트 간 격리를 보장한다.
2. **테스트 데이터는 Java 코드(Fixture Builder + JdbcTemplate)로 준비한다.** SQL 스크립트 대신 Java 코드로 테스트 데이터를 생성하여 가독성과 재사용성을 높인다.
3. **데이터 저장에는 Repository가 아닌 JdbcTemplate을 사용한다.** Repository는 애플리케이션의 내부 구현이므로, 인프라 수준의 JdbcTemplate으로 데이터를 삽입한다.
4. **각 테스트는 독립적으로 실행 가능해야 한다.** 테스트 순서에 의존하지 않는다.

### @Sql에서 Java Fixture로 전환한 이유

리뷰 과정에서 `@Sql` 기반 데이터 준비 전략의 한계를 인식하고, Java Fixture Builder + JdbcTemplate 방식으로 전환했다.

| 관점 | @Sql 방식 | Java Fixture 방식 |
|---|---|---|
| 매직 넘버 | SQL에 ID(1, 2)가 하드코딩됨 | 코드로 의미 부여 가능 (`savedMember.getId()`) |
| 확장성 | 테스트마다 SQL 조합을 작성해야 함 | Fixture 재사용으로 조합 용이 |
| 재사용성 | 파일 단위로만 재사용 가능 | 상태별 Fixture(비활성 유저, 활성 유저 등) 정의 및 재사용 가능 |
| 가독성 | 테스트와 SQL 파일을 오가며 확인 필요 | 테스트 코드 안에서 데이터 의도 파악 가능 |

### 테스트 데이터 계층 구성

Fixture는 "어떤 데이터인가"만 표현하고, "어떻게 저장하는가"는 `TestDataInitializer`에 위임한다. Fixture는 도메인 객체를 반환하고, TestDataInitializer가 그 객체를 받아 JdbcTemplate으로 영속화한다.

| 계층 | 책임 | 예시 |
|---|---|---|
| Fixture | 시나리오별 도메인 객체 생성 | `MemberFixture.일반회원()` → `Member` 반환 |
| TestDataInitializer | 도메인 객체를 JdbcTemplate으로 영속화 | `initializer.saveMember(member)` → ID 반환 |
| DatabaseCleaner | DB 초기화 | `cleaner.clear()` |

```
src/test/java/gift/
├── fixture/
│   ├── MemberFixture.java           # 회원 데이터 정의
│   ├── CategoryFixture.java         # 카테고리 데이터 정의
│   ├── ProductFixture.java          # 상품 데이터 정의
│   └── OptionFixture.java           # 옵션 데이터 정의
└── support/
    ├── TestDataInitializer.java     # JdbcTemplate 기반 데이터 저장
    └── DatabaseCleaner.java         # DB 초기화 유틸리티
```

### Fixture와 TestDataInitializer를 분리하는 이유

Fixture가 JdbcTemplate을 직접 받아 삽입까지 담당하면, 프로젝트가 커질수록 값 정의의 변경과 영속화 로직의 변경이 항상 같은 클래스에서 동시에 일어나는 문제가 생긴다.

**예: Member에 `grade`, `status` 컬럼이 추가되는 경우**

결합된 방식에서는 Fixture 메서드 시그니처가 비대해지거나 오버로딩이 폭발한다.

```java
// 결합된 방식 — 파라미터가 계속 늘어남
static Long insert(JdbcTemplate jt, String name, String email) { ... }
static Long insert(JdbcTemplate jt, String name, String email, Grade grade) { ... }
static Long insert(JdbcTemplate jt, String name, String email, Grade grade, Status status) { ... }
```

분리된 방식에서는 변경 지점이 격리된다.

- **컬럼 추가 시:** `TestDataInitializer.saveMember()`의 params에 한 줄 추가. Fixture의 시나리오 메서드들은 그대로.
- **시나리오 추가 시:** Fixture에만 메서드 추가. TestDataInitializer는 그대로.

### DB 초기화 전략

- `DatabaseCleaner`는 `@Component`로 등록하여 테스트에서 주입받아 사용한다.
- H2 Database의 `SET REFERENTIAL_INTEGRITY FALSE/TRUE`를 활용하여 외래 키 제약 조건 무시 후 TRUNCATE한다.
- 모든 테이블(wish, option, product, member, category)을 초기화한다.

### 테스트 데이터 준비 전략

- **카테고리 테스트:** DB 초기화만 수행. 테스트 내에서 API를 통해 직접 생성한다.
- **상품 테스트:** `CategoryFixture.기본카테고리()`로 도메인 객체를 생성하고 `initializer.saveCategory()`로 저장한 뒤, 반환된 ID로 상품 생성 API를 호출한다.
- **선물 테스트:** `MemberFixture.일반회원()`, `OptionFixture.재고10개옵션()` 등으로 도메인 객체를 생성하고 TestDataInitializer로 저장한 뒤, API를 호출한다. 시나리오의 의도가 Fixture 메서드명에 담기므로 가독성이 높다.

---

## 3. 검증 전략

### 검증 우선순위

```
① HTTP 응답 검증 → ② 조회 API를 통한 검증 → ③ SQL 기반 DB 직접 검증
```

인수 테스트는 원칙적으로 블랙박스 테스트이므로, 사용자 관점에서 확인 가능한 방법을 우선한다.

### 검증 범위 원칙

조회 API로 생성 결과를 검증할 때, ID 존재 여부만 확인하면 "데이터가 있다"는 것만 증명할 뿐 "올바른 데이터가 저장되었다"는 것은 증명하지 못한다. 생성 시 전달한 핵심 필드(name, price 등)가 조회 응답에도 동일하게 포함되어 있는지 반드시 함께 검증한다.

### 시나리오별 검증 방법

| 시나리오 | 검증 방법 | 이유 |
|---|---|---|
| 카테고리 생성 | HTTP 응답 + 조회 API | 생성 응답에서 ID 확인 후, 목록 조회로 ID 존재 여부 및 name 일치 검증 |
| 상품 생성 | HTTP 상태 코드 + 조회 API | 생성 응답에서 ID 확인 후, 목록 조회로 ID 존재 여부 및 name, price 등 핵심 필드 일치 검증 |
| 존재하지 않는 카테고리 | HTTP 상태 코드 (500) | 예외 발생 여부만 확인하면 충분 |
| 선물하기 정상 | HTTP 상태 코드 + **DB 직접 조회** | 옵션 재고 조회 API가 없으므로 JdbcTemplate으로 재고 확인 |
| 재고 부족 | HTTP 상태 코드 + **DB 직접 조회** | 실패 시 재고가 변경되지 않았음(트랜잭션 롤백)을 DB로 확인 |
| 존재하지 않는 옵션 | HTTP 상태 코드 (500) | 예외 발생 여부만 확인하면 충분 |
| 존재하지 않는 발신자 | HTTP 상태 코드 (500) | 예외 발생 여부만 확인하면 충분 |

### DB 직접 조회를 사용한 이유

- 옵션 조회 API가 존재하지 않아 재고 변동을 API로 확인할 수 없다.
- 선물하기 API 응답에 재고 정보가 포함되지 않는다 (void 반환).
- 따라서 `JdbcTemplate`을 사용하여 `option` 테이블의 `quantity` 값을 직접 조회한다.

### 레거시 코드의 한계와 대응

| 한계                                                               | 대응 |
|------------------------------------------------------------------|---|
| CategoryRestController, ProductRestController에 `@RequestBody` 누락 | `ContentType.URLENC` + `formParam()`으로 요청 |
| 상품 생성 시 categoryId가 null로 바인딩되어 500 오류 발생                        | 기대 상태 코드를 500으로 설정하고, 실패 원인을 NOTE 주석으로 문서화 |
| 선물하기 API가 void 반환, 재고 조회 API 부재                                  | DB 직접 조회로 상태 변화 검증 |

---

## 4. 주요 의사결정

### 의사결정 1: 테스트 대상 범위

**결정:** API로 노출된 기능만 테스트한다.

**이유:** 인수 테스트는 사용자 관점에서 시스템의 동작을 검증하는 것이 목적이다. 사용자가 접근할 수 없는 내부 서비스(WishService, OptionService)는 인수 테스트의 범위 밖이다.

### 의사결정 2: 단순 조회 시나리오 통합

**결정:** 생성 시나리오에서 조회를 함께 검증하고, 별도 조회 테스트는 작성하지 않는다. 현재 범위에서는 생성-조회 시나리오로 충분하다고 판단한다. 단 조회 API에 정렬/필터/페이징/권한 같은 독립 요구사항이 생기면 조회 전용 시나리오를 분리한다.

**이유:** "카테고리 생성 후 목록 조회"처럼 생성-조회를 하나의 흐름으로 검증하면, 조회 API의 동작도 자연스럽게 검증된다. 시나리오가 과도하게 세분화되는 것을 방지한다.

### 의사결정 3: 검증 전략 계층화

**결정:** HTTP 응답 → 조회 API → DB 직접 조회 순서의 우선순위를 둔다.

**이유:** 인수 테스트는 사용자 관점의 블랙박스 테스트가 원칙이다. DB 직접 조회는 내부 구현에 의존하므로 최소화하되, 조회 API가 없는 경우(옵션 재고 확인)에 한해 보조 수단으로 사용한다.

### 의사결정 4: 성공/실패 시나리오 모두 포함

**결정:** 정상 시나리오와 예외 시나리오를 모두 테스트한다.

**이유:** 선물하기의 핵심 비즈니스 규칙은 재고 관리이다. 재고 부족 시 안전한 실패(트랜잭션 롤백으로 재고 무변경)는 정상 동작만큼 중요한 검증 대상이다.

### 의사결정 5: 테스트 데이터 준비를 @Sql에서 Java Fixture로 전환

**결정:** `@Sql` 기반 SQL 스크립트 대신 Java Fixture Builder + JdbcTemplate 방식으로 테스트 데이터를 준비한다. 데이터 저장에는 Repository가 아닌 JdbcTemplate을 사용한다.

**이유:** 리뷰를 통해 `@Sql` 방식의 한계를 인식했다. SQL에 ID가 하드코딩되어 매직 넘버가 발생하고, 테스트마다 SQL 조합을 새로 작성해야 하며, 테스트 코드와 SQL 파일을 오가며 읽어야 하는 불편이 있다. Java Fixture는 코드로 데이터 의도를 표현하고, 상태별 Fixture(비활성 유저 등)를 정의하여 재사용할 수 있어 프로젝트가 커질수록 유리하다. 또한 Repository는 애플리케이션의 내부 구현에 해당하므로, 인프라 수준의 JdbcTemplate을 사용하여 테스트와 내부 구현 간 결합을 방지한다.
