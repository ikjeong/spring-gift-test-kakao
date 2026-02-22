---
name: acceptance-test
description: Spring Boot 프로젝트에서 시나리오가 주어졌을 때, 인수 테스트를 작성하는 스킬.
---

# Acceptance Test Skill

Spring Boot + RestAssured 기반 인수 테스트(Acceptance Test)를 작성하는 스킬이다.

## 핵심 원칙

1. **기존 소스코드 절대 수정 금지** — 테스트 코드와 테스트 리소스만 생성한다.
2. **RestAssured given-when-then** — 모든 테스트는 given/when/then 구조로 작성하여 의도를 명시한다.
3. **SQL 기반 데이터 관리** — `@Sql` 어노테이션으로 테스트 데이터 초기화 및 주입을 관리한다.

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

### 3단계: 테스트 SQL 작성

테스트 데이터 관리를 위해 SQL 스크립트를 작성한다.
SQL 파일은 `src/test/resources/` 하위에 배치한다.

**cleanup.sql — DB 초기화:**
```sql
-- 외래 키 제약 조건 때문에 삭제 순서가 중요하다.
-- 자식 테이블 → 부모 테이블 순서로 삭제한다.
-- Entity 관계를 분석하여 올바른 순서를 결정한다.
SET REFERENTIAL_INTEGRITY FALSE;
TRUNCATE TABLE [자식_테이블];
TRUNCATE TABLE [부모_테이블];
SET REFERENTIAL_INTEGRITY TRUE;
```

**[feature]-test-data.sql — 테스트 데이터 주입:**
```sql
-- JPA의 Entity 필드명이 아닌, 실제 DB 컬럼명을 사용해야 한다.
-- @Column(name = "...") 어노테이션이 있으면 그 값을, 없으면 JPA 네이밍 전략(camelCase → snake_case)을 따른다.
-- 예: memberName 필드 → member_name 컬럼
INSERT INTO member (id, name, email) VALUES (1, '테스트유저', 'test@example.com');
```

### 4단계: 인수 테스트 작성

#### 테스트 클래스 기본 구조

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GiftApiTest {

	@LocalServerPort
	int port;

	@BeforeEach
	void setUp() {
		RestAssured.port = port;
	}

	// 테스트마다 DB 초기화 + 테스트 데이터 삽입
	@Sql(scripts = "classpath:cleanup.sql")
	@Sql(scripts = "classpath:gift-test-data.sql")
	@Test
	void gift_생성_테스트() {
		// 예시 JSON 바디
		String body = """
				{
				  "name": "초콜릿",
				  "price": 10000
				}
				""";

		ExtractableResponse<Response> res =
				RestAssured.given().log().all()
						.contentType(ContentType.JSON)
						.header("member-id", "1")
						.body(body)
						.when()
						.post("/api/gifts")
						.then().log().all()
						.statusCode(201)   // 필요에 따라 200/201 등으로 수정
						.extract();

		// 필요하면 후속 검증 예시
		// String id = res.jsonPath().getString("id");
	}
}
```

#### 테스트 메서드 작성 패턴

**패턴 A: API 호출 → 상태 변화 → 재조회로 증명**

데이터를 변경하는 API를 테스트할 때, 변경 후 조회 API를 호출하여 상태가 실제로 변경되었는지 증명한다.

```java
@Sql(scripts = {"/cleanup.sql", "/[feature]-test-data.sql"})
@Test
void 상태_변경_후_조회로_증명() {
    // given — 변경 전 상태 확인
    ExtractableResponse<Response> before = RestAssured.given().log().all()
            .when().get("/api/[resource]/1")
            .then().log().all().extract();
    assertThat(before.jsonPath().getString("status")).isEqualTo("변경_전_상태");

    // when — 상태 변경 API 호출
    RestAssured.given().log().all()
            .contentType(ContentType.JSON)
            .body(Map.of("status", "변경_후_상태"))
            .when().put("/api/[resource]/1")
            .then().log().all()
            .statusCode(HttpStatus.OK.value());

    // then — 변경 후 상태 확인
    ExtractableResponse<Response> after = RestAssured.given().log().all()
            .when().get("/api/[resource]/1")
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
src/test/
├── java/[패키지]/
│   ├── [Feature]AcceptanceTest.java     # 인수 테스트 클래스
│   └── ...
└── resources/
    ├── cleanup.sql                       # DB 초기화 (공통)
    ├── [feature]-test-data.sql           # 기능별 테스트 데이터
    └── ...
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

### 3. DB 직접 조회 없이 검증 가능한가?

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
- **모든 테스트는 독립적으로 실행 가능해야 한다** — `@Sql`로 매 테스트마다 데이터를 초기화하므로 테스트 순서에 의존하지 않는다.
- **RestAssured의 log().all()을 given과 then 양쪽에 붙인다** — 요청과 응답 로그를 모두 출력하여 디버깅을 용이하게 한다.
- **HTTP 상태 코드는 HttpStatus enum을 사용한다** — 매직 넘버(200, 400) 대신 `HttpStatus.OK.value()`, `HttpStatus.BAD_REQUEST.value()`를 사용한다.
- **SQL 스크립트 작성 시 Entity가 아닌 실제 DB 컬럼명을 사용한다** — JPA 네이밍 전략에 의해 camelCase가 snake_case로 변환될 수 있다.
- **H2 Database 호환 SQL을 작성한다** — MySQL이나 PostgreSQL 전용 문법을 사용하지 않는다.

## 테스트 실행 확인

테스트 작성 후 반드시 실행하여 통과 여부를 확인한다.

```bash
# Gradle
./gradlew test --tests "[패키지].[Feature]AcceptanceTest"
```

실패 시 로그를 확인하고:
1. SQL 스크립트의 컬럼명이 실제 DB 스키마와 일치하는지 확인
2. 요청 본문의 필드명이 DTO와 일치하는지 확인
3. API 경로가 Controller의 매핑과 일치하는지 확인
