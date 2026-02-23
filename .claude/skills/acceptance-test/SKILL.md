---
name: acceptance-test
description: Spring Boot 프로젝트에서 시나리오가 주어졌을 때, 인수 테스트를 작성하는 스킬.
---

# Acceptance Test Skill

Spring Boot + RestAssured 기반 인수 테스트(Acceptance Test)를 작성하는 스킬이다.

## 핵심 원칙

1. **기존 소스코드 절대 수정 금지** — 테스트 코드와 테스트 리소스만 생성한다.
2. **RestAssured given-when-then** — 모든 테스트는 given/when/then 구조로 작성하여 의도를 명시한다.
3. **Java Fixture Builder + JdbcTemplate 기반 데이터 관리** — SQL 스크립트 대신 Java 코드로 테스트 데이터를 생성하고, JdbcTemplate을 통해 DB에 삽입한다. Repository는 애플리케이션 내부 구현이므로 사용하지 않는다. `DatabaseCleaner`로 DB를 초기화한다.

---

## 기술 스택

- Spring Boot 3.x
- Java 21
- H2 Database + JPA
- RestAssured (테스트)
- JUnit 5

---

## 작업 절차

### 1단계: 프로젝트 구조 분석

테스트 대상을 파악하기 위해 프로젝트 구조를 먼저 분석한다.

```
# 반드시 아래 항목들을 확인한다
1. build.gradle — 의존성 확인 (RestAssured가 없으면 추가 안내)
2. src/main/java 하위 — Controller, Service, Repository, Entity 구조 파악
3. src/main/resources/application.yml — DB 설정, 서버 설정 확인
4. src/test 하위 — 기존 테스트 코드가 있는지 확인
```

**확인할 것:**
- API 엔드포인트 목록 (Controller의 `@RequestMapping`, `@GetMapping`, `@PostMapping` 등)
- 요청/응답 DTO 구조
- Entity 필드 및 관계 (테이블 구조 파악)
- 비즈니스 로직의 검증 조건 (Service 레이어의 예외 발생 조건)

### 2단계: 의존성 확인

`build.gradle`에 아래 의존성이 있는지 확인한다. 없으면 사용자에게 추가를 안내한다.

**Gradle:**
```groovy
dependencies {
    testImplementation 'io.rest-assured:rest-assured:5.5.1'
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```

### 3단계: Fixture, TestDataInitializer, DatabaseCleaner 작성

테스트 데이터 관리를 위해 세 계층을 작성한다. Fixture는 "어떤 데이터인가"만 정의하고(도메인 객체 반환), "어떻게 저장하는가"는 TestDataInitializer에 위임한다(JdbcTemplate 영속화). 이렇게 분리하면 컬럼 추가 시 TestDataInitializer만, 시나리오 추가 시 Fixture만 수정하면 된다.

**DatabaseCleaner — DB 초기화:**

`@Component`로 등록하여 테스트에서 주입받아 사용한다. 모든 테이블을 TRUNCATE하여 테스트 간 격리를 보장한다.

```java
@Component
public class DatabaseCleaner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void clear() {
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        // Entity 관계를 분석하여 모든 테이블을 TRUNCATE한다.
        jdbcTemplate.execute("TRUNCATE TABLE [테이블명]");
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }
}
```

**Fixture 클래스 — 시나리오별 도메인 객체 생성만 담당:**

각 도메인별로 Fixture 클래스를 작성한다. DB 저장 로직 없이 도메인 객체만 반환한다. 메서드명으로 시나리오의 의도를 표현한다.

```java
public class MemberFixture {

    public static Member 일반회원() {
        return new Member("테스트유저", "test@example.com");
    }

    public static Member 발신회원() {
        return new Member("보내는사람", "sender@test.com");
    }

    public static Member 수신회원() {
        return new Member("받는사람", "receiver@test.com");
    }
}
```

**TestDataInitializer — 도메인 객체를 JdbcTemplate으로 영속화:**

`@Component`로 등록하여 Fixture가 생성한 도메인 객체를 DB에 저장하고, 생성된 ID를 반환한다. Repository는 애플리케이션 내부 구현이므로 테스트에서 사용하지 않는다.

```java
@Component
public class TestDataInitializer {

    private final JdbcTemplate jdbcTemplate;

    public TestDataInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long saveMember(Member member) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("member")
                .usingGeneratedKeyColumns("id");
        Map<String, Object> params = Map.of(
                "name", member.getName(),
                "email", member.getEmail()
        );
        return insert.executeAndReturnKey(params).longValue();
    }

    public Long saveCategory(Category category) {
        SimpleJdbcInsert insert = new SimpleJdbcInsert(jdbcTemplate)
                .withTableName("category")
                .usingGeneratedKeyColumns("id");
        Map<String, Object> params = Map.of("name", category.getName());
        return insert.executeAndReturnKey(params).longValue();
    }

    // 도메인별 save 메서드 추가
}
```

```java
// 테스트에서의 사용 — 시나리오의 의도가 메서드명에 담김
Long senderId = initializer.saveMember(MemberFixture.발신회원());
Long receiverId = initializer.saveMember(MemberFixture.수신회원());
```

### 4단계: 인수 테스트 작성

#### 테스트 클래스 기본 구조

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiftApiTest {

	@LocalServerPort
	int port;

	@Autowired
	DatabaseCleaner databaseCleaner;

	@Autowired
	TestDataInitializer initializer;

	@BeforeEach
	void setUp() {
		RestAssured.port = port;
		databaseCleaner.clear();
	}

	@Test
	void gift_생성_테스트() {
		// given — Fixture(도메인 객체 생성) + TestDataInitializer(영속화)
		Long senderId = initializer.saveMember(MemberFixture.발신회원());
		Long receiverId = initializer.saveMember(MemberFixture.수신회원());
		Long categoryId = initializer.saveCategory(CategoryFixture.기본카테고리());
		Long productId = initializer.saveProduct(ProductFixture.기본상품(categoryId));
		Long optionId = initializer.saveOption(OptionFixture.재고10개옵션(productId));

		String body = """
				{
				  "optionId": %d,
				  "quantity": 3,
				  "receiverId": %d,
				  "message": "생일 축하해!"
				}
				""".formatted(optionId, receiverId);

		// when
		ExtractableResponse<Response> res =
				RestAssured.given().log().all()
						.contentType(ContentType.JSON)
						.header("Member-Id", senderId)
						.body(body)
						.when()
						.post("/api/gifts")
						.then().log().all()
						.statusCode(HttpStatus.OK.value())
						.extract();

		// then — 매직 넘버 없이 ID로 검증
	}
}
```

#### 테스트 메서드 작성 패턴

**패턴 A: API 호출 → 상태 변화 → 재조회로 증명**

데이터를 변경하는 API를 테스트할 때, 변경 후 조회 API를 호출하여 상태가 실제로 변경되었는지 증명한다.

```java
@Test
void 상태_변경_후_조회로_증명() {
    // given — Fixture(도메인 객체 생성) + TestDataInitializer(영속화)
    Long resourceId = initializer.saveResource(ResourceFixture.변경전상태());

    // given — 변경 전 상태 확인
    ExtractableResponse<Response> before = RestAssured.given().log().all()
            .when().get("/api/[resource]/" + resourceId)
            .then().log().all().extract();
    assertThat(before.jsonPath().getString("status")).isEqualTo("변경_전_상태");

    // when — 상태 변경 API 호출
    RestAssured.given().log().all()
            .contentType(ContentType.JSON)
            .body(Map.of("status", "변경_후_상태"))
            .when().put("/api/[resource]/" + resourceId)
            .then().log().all()
            .statusCode(HttpStatus.OK.value());

    // then — 변경 후 상태 확인
    ExtractableResponse<Response> after = RestAssured.given().log().all()
            .when().get("/api/[resource]/" + resourceId)
            .then().log().all().extract();
    assertThat(after.jsonPath().getString("status")).isEqualTo("변경_후_상태");
}
```

---

## 테스트 메서드 명명 규칙

한글 메서드명을 사용하여 테스트 의도를 명확히 드러낸다.

```
[상황]_[행동]하면_[결과]한다

예시:
- 잔액이_부족할_때_결제하면_실패한다
- 유효한_쿠폰으로_결제하면_할인이_적용된다
- 이미_취소된_주문을_다시_취소하면_실패한다
- 존재하지_않는_상품을_조회하면_404를_반환한다
```

---

## 파일 배치 규칙

```
src/test/java/[패키지]/
├── [Feature]AcceptanceTest.java     # 인수 테스트 클래스
├── fixture/
│   ├── MemberFixture.java           # 회원 데이터 정의
│   ├── CategoryFixture.java         # 카테고리 데이터 정의
│   ├── ProductFixture.java          # 상품 데이터 정의
│   └── OptionFixture.java           # 옵션 데이터 정의
└── support/
    ├── TestDataInitializer.java     # JdbcTemplate 기반 데이터 저장
    └── DatabaseCleaner.java         # DB 초기화 유틸리티
```

## 검증 전략
### 1. HTTP 응답만으로 충분한가?

인수 테스트는 단순히 HTTP 상태 코드만 검증하는 테스트가 아니다.
비즈니스 요구사항에 따라 실제 데이터 상태 변화까지 검증해야 할 수 있다.

검증 기준은 다음과 같다.
- HTTP 응답에 상태 정보가 충분히 포함되어 있다면 → 응답 기반 검증
- 응답에 내부 상태 변화가 포함되지 않는다면 → 조회 API 또는 SQL 기반 검증

### 2. 상태 변화 확인 방법

상태 변화는 다음 우선순위에 따라 확인한다.

① 조회 API를 통한 검증 (권장)

가능하다면 DB 직접 조회 대신 조회 API를 호출하여 상태를 검증한다.
```java
// 변경 후 재조회
ExtractableResponse<Response> after = RestAssured.given().log().all()
.when().get("/api/resource/1")
.then().log().all()
.statusCode(HttpStatus.OK.value())
.extract();

assertThat(after.jsonPath().getString("status"))
.isEqualTo("변경_후_상태");
```
인수 테스트는 “사용자 관점” 검증이므로, 내부 DB 직접 조회보다 API를 통한 재조회가 더 적절하다.

② SQL 기반 검증 (보조 수단)

다음과 같은 경우에는 SQL을 통해 직접 DB 상태를 확인할 수 있다.
- 조회 API가 존재하지 않는 경우 
- 삭제 여부를 확인해야 하는 경우 
- 응답에 상태 정보가 포함되지 않는 경우 
- 내부 데이터 정합성을 검증해야 하는 경우  

단, SQL 기반 검증은 최소화한다.

### 4. 존재 여부만이 아닌, 데이터 내용까지 검증

생성 후 조회로 검증할 때 ID만 확인하면 "레코드가 존재한다"는 것만 증명된다.
생성 시 전달한 핵심 필드(name, price 등)가 조회 응답에도 동일하게 포함되어 있는지 반드시 함께 검증한다.

### 5. DB 직접 조회 없이 검증 가능한가?

원칙적으로 인수 테스트는 블랙박스 테스트다.
따라서 다음 조건이 충족되면 DB 직접 조회는 필요 없다.
- 상태 변경 결과가 HTTP 응답에 포함된다.
- 또는 변경 후 조회 API로 상태 확인이 가능하다.

DB 직접 조회는 다음 경우에만 사용한다.
- API로 확인 불가능한 내부 데이터 검증
- 데이터 삭제 여부 확인
- 복합 트랜잭션 정합성 검증

---

## 주의사항

### 절대 하지 말 것
- **기존 src/main 하위 소스코드 수정 금지** — Controller, Service, Repository, Entity, DTO, 설정 파일 등 어떤 것도 수정하지 않는다.
- **테스트용 API 엔드포인트 추가 금지** — 기존 API만으로 테스트한다.
- **application.yml 수정 금지** — 테스트 전용 설정이 필요하면 `src/test/resources/application.yml`을 별도로 생성한다.

### 반드시 할 것
- **모든 테스트는 독립적으로 실행 가능해야 한다** — `@BeforeEach`에서 `DatabaseCleaner`로 매 테스트마다 데이터를 초기화하므로 테스트 순서에 의존하지 않는다.
- **Fixture는 도메인 객체 생성만, TestDataInitializer는 영속화만 담당한다** — Fixture에 JdbcTemplate을 전달하지 않는다. 컬럼 추가 시 TestDataInitializer만, 시나리오 추가 시 Fixture만 수정하면 된다.
- **Fixture 메서드명으로 시나리오의 의도를 표현한다** — `create("보내는사람", ...)` 대신 `발신회원()`, `수신회원()` 같이 데이터의 역할을 메서드명에 담는다.
- **TestDataInitializer가 반환하는 ID를 코드로 참조한다** — `initializer.saveXxx()`가 반환하는 ID를 사용하여 하드코딩된 ID(1, 2 등)를 제거한다.
- **RestAssured의 log().all()을 given과 then 양쪽에 붙인다** — 요청과 응답 로그를 모두 출력하여 디버깅을 용이하게 한다.
- **HTTP 상태 코드는 HttpStatus enum을 사용한다** — 매직 넘버(200, 400) 대신 `HttpStatus.OK.value()`, `HttpStatus.BAD_REQUEST.value()`를 사용한다.
- **조회 API로 생성을 검증할 때, ID뿐 아니라 핵심 필드(name 등)도 함께 검증한다** — ID 존재 여부만 확인하면 "데이터가 있다"는 것만 증명될 뿐, 올바른 데이터가 저장되었는지는 보장되지 않는다.

## 테스트 실행 확인

테스트 작성 후 반드시 실행하여 통과 여부를 확인한다.

```bash
# Gradle
./gradlew test --tests "[패키지].[Feature]AcceptanceTest"
```

실패 시 로그를 확인하고:
1. TestDataInitializer의 INSERT 컬럼명이 실제 DB 스키마(JPA 네이밍 전략: camelCase → snake_case)와 일치하는지 확인
2. 요청 본문의 필드명이 DTO와 일치하는지 확인
3. API 경로가 Controller의 매핑과 일치하는지 확인
