# 테스트 전략

## 1. 검증 대상 시나리오

### 선정 기준

- API로 노출된 기능만 대상. 내부 서비스(WishService, OptionService 등)는 제외
- 성공/실패 시나리오 모두 포함. 예외 상황에서의 안전한 실패도 핵심 요구사항
- 단순 조회는 생성 시나리오에서 함께 검증. 별도 조회 테스트 불필요

### 시나리오 목록

| # | 도메인 | 시나리오 | .feature |
|---|---|---|---|
| 1 | 카테고리 | 카테고리를 생성하면 목록 조회 시 조회된다 | category.feature |
| 2 | 상품 | 상품을 생성하면 목록 조회 시 조회된다 | product.feature |
| 3 | 상품 | 존재하지 않는 카테고리로 상품을 생성하면 실패한다 | product.feature |
| 4 | 선물 | 선물하기가 정상 처리되면 옵션 재고가 차감된다 | gift.feature |
| 5 | 선물 | 재고보다 많은 수량을 선물하면 실패하고 재고는 변경되지 않는다 | gift.feature |
| 6 | 선물 | 존재하지 않는 옵션으로 선물하면 실패한다 | gift.feature |
| 7 | 선물 | 존재하지 않는 발신자로 선물하면 실패한다 | gift.feature |

---

## 2. Cucumber 아키텍처

### 구성요소

| 컴포넌트 | 파일 | 역할 |
|---|---|---|
| Spring 통합 | `CucumberSpringConfiguration` | `@CucumberContextConfiguration` + `@SpringBootTest(NONE)`. Cucumber와 Spring 컨텍스트를 연결. `NONE`은 임베디드 서버 없이 ApplicationContext만 로드 |
| 러너 | `RunCucumberTest` | `@Suite` + `@IncludeEngines("cucumber")`. Gradle/IDE가 시나리오를 발견하는 진입점 |
| 엔진 설정 | `junit-platform.properties` | `cucumber.glue`, `cucumber.features`, `cucumber.plugin` 설정. Java 코드와 분리하여 변경 시 재컴파일 불필요 |
| 상태 공유 | `ScenarioContext` | `@Component` + `@ScenarioScope`. 시나리오마다 새 인스턴스 생성/폐기. Step Definition 클래스 간 상태(ID, 응답) 공유 |
| 전처리 | `CucumberHooks` | `@Before`에서 RestAssured port=8080 설정 + `DatabaseCleaner.clear()`. 횡단 관심사를 Step Definition에서 분리 |

### 파일 구조

```
src/test/
├── java/gift/
│   ├── cucumber/
│   │   ├── CucumberSpringConfiguration.java
│   │   ├── RunCucumberTest.java
│   │   ├── ScenarioContext.java
│   │   ├── CucumberHooks.java
│   │   └── steps/
│   │       ├── CategoryStepDefinitions.java
│   │       ├── ProductStepDefinitions.java
│   │       └── GiftStepDefinitions.java
│   ├── fixture/
│   │   ├── MemberFixture.java
│   │   ├── CategoryFixture.java
│   │   ├── ProductFixture.java
│   │   └── OptionFixture.java
│   └── support/
│       ├── TestDataInitializer.java
│       └── DatabaseCleaner.java
├── resources/
│   ├── application-e2e.properties
│   ├── junit-platform.properties
│   └── features/
│       ├── category.feature
│       ├── product.feature
│       └── gift.feature
```

### Spring 컨텍스트에서 사용하는 빈

`@SpringBootTest(NONE)`이지만 Spring 컨텍스트가 필요한 이유: 아래 4개 빈이 Spring 기능에 의존한다.

| 빈 | 역할 | 의존하는 Spring 기능 |
|---|---|---|
| `DatabaseCleaner` | 테이블 TRUNCATE | `@Component`, `JdbcTemplate` |
| `TestDataInitializer` | 테스트 데이터 삽입 | `@Component`, `SimpleJdbcInsert`, `DataSource` 자동 구성 |
| `ScenarioContext` | 시나리오 간 상태 공유 | `@Component`, `@ScenarioScope` |
| `JdbcTemplate` | DB 직접 조회 (재고 확인 등) | Spring Boot 자동 구성 |

---

## 3. 테스트 데이터 전략

### 3계층 구조

Fixture는 "어떤 데이터인가"만 표현하고, "어떻게 저장하는가"는 TestDataInitializer에 위임한다. 이렇게 분리하면 컬럼 추가 시 TestDataInitializer만, 시나리오 추가 시 Fixture만 수정하면 된다.

| 계층 | 책임 | 예시 |
|---|---|---|
| **Fixture** | 시나리오별 도메인 객체 생성 | `MemberFixture.일반회원()` → `Member` 반환 |
| **TestDataInitializer** | 도메인 객체를 JdbcTemplate으로 영속화 | `initializer.saveMember(member)` → ID 반환 |
| **DatabaseCleaner** | DB 초기화 | `cleaner.clear()` → TRUNCATE + RESTART IDENTITY CASCADE |

### 분리의 이점

**컬럼 추가 시 (예: Member에 `grade` 추가):**
- Fixture의 시나리오 메서드들은 그대로
- TestDataInitializer의 `saveMember()` params에 한 줄 추가

**시나리오 추가 시 (예: VIP회원 테스트):**
- Fixture에 `VIP회원()` 메서드 추가
- TestDataInitializer는 그대로

### Fixture 설계 원칙

- 메서드명으로 시나리오 의도를 표현한다: `create("보내는사람")` 대신 `발신회원()`
- DB 저장 로직 없이 도메인 객체만 반환한다
- JdbcTemplate을 Fixture에 전달하지 않는다

### TestDataInitializer 설계 원칙

- Repository 대신 `SimpleJdbcInsert`(JdbcTemplate)를 사용한다. Repository는 앱 내부 구현이므로 테스트와의 결합을 방지
- 반환하는 ID를 코드로 참조하여 매직 넘버(1, 2 등) 제거

### DB 초기화

PostgreSQL의 `TRUNCATE TABLE ... RESTART IDENTITY CASCADE`를 사용하여 외래 키 제약 무시 + 시퀀스 리셋. 모든 테이블을 초기화한다.

---

## 4. 검증 전략

### 우선순위

```
① HTTP 응답 검증 → ② 조회 API → ③ DB 직접 조회 (JdbcTemplate)
```

인수 테스트는 블랙박스 테스트가 원칙. 사용자 관점에서 확인 가능한 방법을 우선한다.

### 시나리오별 검증 방법

| 시나리오 | 검증 방법 | 이유 |
|---|---|---|
| 카테고리 생성 | HTTP 응답 + 조회 API | 생성 응답의 ID + 목록 조회로 name 일치 검증 |
| 상품 생성 | HTTP 상태 코드 + 조회 API | ID + name, price 등 핵심 필드 일치 검증 |
| 존재하지 않는 카테고리 | HTTP 상태 코드 (500) | 예외 발생 여부만 확인 |
| 선물하기 정상 | HTTP 상태 코드 + **DB 직접 조회** | 옵션 재고 조회 API 없음. JdbcTemplate으로 재고 확인 |
| 재고 부족 | HTTP 상태 코드 + **DB 직접 조회** | 재고 무변경(롤백)을 DB로 확인 |
| 존재하지 않는 옵션 | HTTP 상태 코드 (500) | 예외 발생 여부만 확인 |
| 존재하지 않는 발신자 | HTTP 상태 코드 (500) | 예외 발생 여부만 확인 |

### 핵심 원칙: 데이터 내용까지 검증

ID 존재 여부만 확인하면 "레코드가 있다"는 것만 증명된다. 생성 시 전달한 핵심 필드(name, price 등)가 조회 응답에도 동일한지 반드시 검증한다.

### 레거시 코드의 한계와 대응

| 한계 | 대응 |
|---|---|
| 선물하기 API가 void 반환, 재고 조회 API 부재 | DB 직접 조회로 상태 변화 검증 |

---

## 5. 주요 의사결정

### 테스트 대상 범위

**결정:** API로 노출된 기능만 테스트.
**근거:** 사용자가 접근할 수 없는 내부 서비스는 인수 테스트 범위 밖.

### 조회 시나리오 통합

**결정:** 생성 시나리오에서 조회를 함께 검증. 별도 조회 테스트 미작성.
**근거:** 생성-조회를 하나의 흐름으로 검증하면 시나리오 과잉 세분화 방지. 정렬/필터/페이징 요구사항이 생기면 분리.

### 검증 전략 계층화

**결정:** HTTP 응답 → 조회 API → DB 직접 조회 우선순위.
**근거:** 블랙박스 원칙. DB 직접 조회는 조회 API 부재 시에만 보조 수단.

### 성공/실패 시나리오 모두 포함

**결정:** 예외 시나리오도 테스트.
**근거:** 재고 부족 시 안전한 실패(트랜잭션 롤백)는 정상 동작만큼 중요.

### 상태 공유 객체 구현

**결정:** 필드 기반 `ScenarioContext`. Map이 아닌 타입이 있는 필드로 상태를 관리.
**근거:** Map 기반(`Map<String, Object>`)은 키 오타나 타입 캐스팅 오류를 런타임에서야 발견한다. 필드 기반은 컴파일 타임에 잡을 수 있다. 필드 방식은 도메인이 늘어날 때 클래스가 비대해질 수 있으나, 도메인별 Context 객체를 분리하면 해결 가능하다.

### 테스트 데이터 준비

**결정:** Java Fixture Builder + JdbcTemplate. Repository 사용 안 함.
**근거:** SQL 스크립트 대비 매직 넘버 제거, Fixture 재사용, 코드 내 가독성 확보. Repository는 앱 내부 구현이므로 테스트 결합 방지.

---

## 6. 대안 분석

### Spring 컨텍스트

| 방식 | 장점 | 단점 |
|---|---|---|
| **현재: `@SpringBootTest(NONE)`** | DataSource 자동 구성, `SimpleJdbcInsert`, `@ScenarioScope` | 컨텍스트 로딩 ~2-3초 |
| PicoContainer + 순수 JDBC | 시작 시간 제거, 의존성 단순 | Connection 풀 수동 관리, PreparedStatement 수동 작성 |

**선택 근거:** 4개 빈의 편의성 대비 ~2-3초 로딩 비용은 수용 가능.

### webEnvironment

| 방식 | 장점 | 단점 |
|---|---|---|
| **현재: `NONE`** | 임베디드 서버 불필요. 빈만 사용 | 앱이 외부 실행 전제 |
| `RANDOM_PORT` | `@LocalServerPort` 자동 주입, Docker 불필요 | Dockerfile/컨테이너 통신 검증 불가 |

**선택 근거:** 앱은 Docker 컨테이너에서 실행되므로 테스트 JVM에서 서버를 띄울 이유 없음.

### DB 초기화

| 방식 | 장점 | 단점 |
|---|---|---|
| **현재: JdbcTemplate TRUNCATE** | Connection 자동 관리, 예외 자동 변환 | Spring 컨텍스트 필요 |
| 순수 JDBC | Spring 불필요 | Connection 수동 open/close, 누수 위험 |

### 데이터 저장

| 방식 | 장점 | 단점 |
|---|---|---|
| **현재: SimpleJdbcInsert** | Map 기반 간결, `executeAndReturnKey()` | Spring 필요 |
| PreparedStatement | 순수 JDBC | `getGeneratedKeys()` 수동 처리 |
| Repository | 가장 간결 | 앱 구현과 결합 |

### 상태 공유 (라이프사이클)

| 방식 | 장점 | 단점 |
|---|---|---|
| **현재: `@ScenarioScope`** | 시나리오마다 자동 생성/폐기 | Spring 필요 |
| PicoContainer | Spring 불필요, 생성자 주입 | 별도 의존성 추가 |
| ThreadLocal | 주입 없음 | 수동 정리 필요, 누출 위험 |

### 상태 공유 (구현 방식)

| 방식 | 장점 | 단점 |
|---|---|---|
| **현재: 필드 기반** | 컴파일 타임 타입 검증. 오타·캐스팅 오류 방지 | 도메인 추가 시 필드 증가. 도메인별 Context 분리로 대응 가능 |
| `Map<String, Object>` | 필드 추가 없이 유연하게 확장 | 키 오타를 런타임에서야 발견. 타입 캐스팅 필요 |
