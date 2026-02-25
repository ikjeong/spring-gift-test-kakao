---
name: acceptance-test
description: Spring Boot 프로젝트에서 시나리오가 주어졌을 때, Cucumber BDD 인수 테스트를 작성하는 스킬.
---

# Acceptance Test Skill

Cucumber BDD + RestAssured + Docker 기반 인수 테스트를 작성하는 스킬이다.

## 핵심 원칙

1. **기존 소스코드 절대 수정 금지** — `src/main` 하위는 어떤 것도 수정하지 않는다.
2. **Cucumber BDD** — `.feature` 파일(한글 Gherkin) + Step Definitions으로 작성한다.
3. **Java Fixture Builder + JdbcTemplate** — Fixture(도메인 객체 반환) + TestDataInitializer(JdbcTemplate 영속화). Repository 사용 금지.

---

## 기술 스택

- Spring Boot 3.x, Java 21
- PostgreSQL (Docker 컨테이너)
- Cucumber 7.x + RestAssured
- `@SpringBootTest(webEnvironment = NONE)` — 앱은 Docker 컨테이너에서 실행
- RestAssured → `localhost:8080` (Docker 앱 컨테이너)

---

## 작업 절차

### 1단계: 프로젝트 분석

```
1. build.gradle — 의존성 확인
2. src/main/java — Controller, Entity, DTO 구조 파악
3. src/test/java/gift/cucumber — 기존 테스트·Step Definition 확인
4. src/test/resources/features — 기존 .feature 파일 확인
```

### 2단계: .feature 파일 작성

한글 Gherkin으로 시나리오를 작성한다. 키워드(Feature/Scenario/Given/When/Then)는 영어, Step 텍스트는 한글.

```gherkin
Feature: 카테고리 관리

  Scenario: 카테고리를 생성하면 목록 조회 시 조회된다
    Given "전자제품" 카테고리를 등록한다
    When 카테고리 목록을 조회한다
    Then 카테고리 목록에 "전자제품"이 포함되어 있다
```

원칙:
- 비즈니스 용어 사용 (기술 용어 HTTP, JSON, API 금지)
- 구현이 바뀌어도 Gherkin은 유지되어야 함
- Background로 공통 전제 조건 추출
- **시나리오-구현 필드 일치** — Step Definition이 전송하거나 검증하는 모든 핵심 필드는 Gherkin 시나리오 텍스트에 표현되어야 한다. 시나리오에 드러나지 않는 숨겨진 필드가 있으면 시나리오의 의도가 불명확해진다.
  - 예: 상품 등록 시 name, price, imageUrl, categoryId를 모두 전송한다면, 시나리오 텍스트에도 이 필드들이 표현되어야 한다
  - Background에서 준비한 컨텍스트(카테고리 등)를 참조할 때는 "해당 카테고리에"처럼 명시적으로 표현한다

### 3단계: Fixture/TestDataInitializer/DatabaseCleaner 작성 (또는 기존 재사용)

**Fixture** — 도메인 객체 생성만 담당. 메서드명으로 시나리오 의도 표현:

```java
public class MemberFixture {
    public static Member 발신회원() {
        return new Member("보내는사람", "sender@test.com");
    }
}
```

**TestDataInitializer** — 도메인 객체를 JdbcTemplate으로 영속화:

```java
@Component
public class TestDataInitializer {
    private final SimpleJdbcInsert memberInsert;

    public TestDataInitializer(DataSource dataSource) {
        this.memberInsert = new SimpleJdbcInsert(dataSource)
            .withTableName("member").usingGeneratedKeyColumns("id");
    }

    public Long saveMember(Member member) {
        return memberInsert.executeAndReturnKey(Map.of(
            "name", member.getName(), "email", member.getEmail()
        )).longValue();
    }
}
```

**DatabaseCleaner** — PostgreSQL TRUNCATE:

```java
@Component
public class DatabaseCleaner {
    @Autowired private JdbcTemplate jdbcTemplate;

    public void clear() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE wish, option, product, member, category RESTART IDENTITY CASCADE"
        );
    }
}
```

### 4단계: Step Definitions 작성

```java
public class CategoryStepDefinitions {
    @Autowired private ScenarioContext context;

    @Given("{string} 카테고리를 등록한다")
    public void 카테고리를_등록한다(String name) {
        var response = RestAssured.given().log().all()
                .contentType(ContentType.JSON)
                .body(Map.of("name", name))
                .when().post("/api/categories")
                .then().log().all()
                .statusCode(HttpStatus.OK.value())
                .extract();
        context.setCategoryId(response.jsonPath().getLong("id"));
    }
}
```

---

## 테스트 구조

### Spring 통합

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class CucumberSpringConfiguration {}
```

- `NONE`: 앱은 Docker 컨테이너에서 실행. 테스트 JVM에서 서블릿 컨테이너 불필요
- Spring 컨텍스트는 전체 Cucumber 실행에서 1회만 기동

### 러너

```java
@Suite
@IncludeEngines("cucumber")
public class RunCucumberTest {}
```

### 시나리오 전처리

```java
public class CucumberHooks {
    @Autowired DatabaseCleaner databaseCleaner;

    @Before
    public void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = 8080;
        databaseCleaner.clear();
    }
}
```

### 상태 공유

```java
@Component
@ScenarioScope
public class ScenarioContext {
    private ExtractableResponse<Response> response;
    private int statusCode;
    private Long categoryId;
    // ...
}
```

---

## 검증 전략

### 우선순위

```
① HTTP 응답 → ② 조회 API → ③ DB 직접 조회 (JdbcTemplate)
```

### 원칙

- ID뿐 아니라 핵심 필드(name, price 등)도 반드시 검증
- DB 직접 조회는 조회 API 부재 시에만 보조 수단으로 사용
- 블랙박스 테스트 원칙: 사용자 관점 검증 우선

---

## 주의사항

### 절대 하지 말 것
- `src/main` 하위 코드 수정
- 테스트용 API 엔드포인트 추가
- Repository를 테스트 데이터 저장에 사용
- Fixture에 JdbcTemplate 전달

### 반드시 할 것
- Fixture는 도메인 객체 반환만, TestDataInitializer는 영속화만
- `initializer.saveXxx()` 반환 ID를 참조하여 매직 넘버 제거
- `RestAssured.given().log().all()` + `.then().log().all()` 양쪽 로그 출력
- HTTP 상태 코드는 `HttpStatus` enum 사용

---

## 테스트 실행

```bash
# 사전: Docker 컨테이너 시작
./gradlew dockerUp

# Cucumber 인수 테스트 실행
./gradlew cucumberTest

# 사후: 컨테이너 정리
./gradlew dockerDown
```

---

## 파일 배치 규칙

```
src/test/
├── java/gift/
│   ├── cucumber/
│   │   ├── CucumberSpringConfiguration.java   # Spring 통합
│   │   ├── RunCucumberTest.java               # 러너
│   │   ├── ScenarioContext.java               # 상태 공유
│   │   ├── CucumberHooks.java                 # 전처리
│   │   └── steps/
│   │       └── [Domain]StepDefinitions.java   # Step 구현
│   ├── fixture/
│   │   └── [Domain]Fixture.java               # 데이터 정의
│   └── support/
│       ├── TestDataInitializer.java           # 데이터 영속화
│       └── DatabaseCleaner.java               # DB 초기화
├── resources/
│   ├── application-e2e.properties             # E2E 프로파일
│   ├── junit-platform.properties              # Cucumber 설정
│   └── features/
│       └── [domain].feature                   # 시나리오
```
