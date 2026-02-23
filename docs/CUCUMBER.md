# Cucumber 구성요소 학습

## 1. 개요

기존 RestAssured 기반 인수 테스트를 Cucumber BDD 형식으로 전환하면서, Cucumber가 Spring Boot와 어떻게 통합되는지 학습했다. 이 문서는 Cucumber 테스트를 실행하기 위해 필요한 구성요소 각각의 역할과 존재 이유를 정리한다.

---

## 2. 구성요소

### 2.1 CucumberSpringConfiguration — Spring 통합의 진입점

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {}
```

**역할:** Cucumber와 Spring ApplicationContext를 연결한다.

**왜 필요한가?**
- Cucumber는 자체적으로 Spring을 모른다. `cucumber-spring` 모듈이 `@CucumberContextConfiguration`이 붙은 클래스를 찾아 Spring ApplicationContext를 부트스트랩한다.
- 이 클래스가 없으면 Step Definition에서 `@Autowired`가 동작하지 않는다.
- `RANDOM_PORT`는 실제 서블릿 컨테이너를 띄워 RestAssured로 HTTP 요청을 보내기 위한 전략이다.

**왜 빈 클래스인가?**
- 설정 책임만 가진다. 테스트 로직이나 훅을 넣으면 역할이 섞인다.
- Spring 컨텍스트는 전체 Cucumber 실행에서 1회만 기동된다. 시나리오마다 재기동하지 않는다.

### 2.2 RunCucumberTest — JUnit Platform Suite 러너

```java
@Suite
@IncludeEngines("cucumber")
public class RunCucumberTest {}
```

**역할:** Gradle/IDE가 Cucumber 시나리오를 발견하고 실행할 수 있는 진입점을 제공한다.

**왜 필요한가?**
- Gradle의 `useJUnitPlatform()`은 JUnit Platform 엔진을 탐색한다. Cucumber는 자체 엔진(`cucumber`)을 제공하지만, 빌드 도구에서 이를 인식하려면 진입점이 필요하다.
- `@Suite` + `@IncludeEngines("cucumber")`가 그 진입점 역할을 한다.
- 이 클래스가 없으면 `./gradlew test`에서 Cucumber 시나리오가 발견되지 않을 수 있다.

**왜 설정이 여기 없고 junit-platform.properties에 있는가?**
- `@SelectClasspathResource("features")`, `@ConfigurationParameter(key = GLUE_PROPERTY_NAME, ...)` 등을 이 클래스에 붙일 수도 있다.
- 하지만 `junit-platform.properties`에 두면 러너 클래스와 설정값이 분리되어, 설정 변경 시 Java 코드를 수정할 필요가 없다.

### 2.3 junit-platform.properties — Cucumber 엔진 설정

```properties
cucumber.glue=gift.cucumber
cucumber.features=src/test/resources/features
cucumber.plugin=pretty
```

| 속성 | 설명 |
|---|---|
| `cucumber.glue` | Step Definition, Hook, Spring Configuration을 스캔할 패키지. `gift.cucumber` 하위의 모든 클래스를 탐색한다. |
| `cucumber.features` | `.feature` 파일 경로. Cucumber가 여기서 시나리오를 읽는다. |
| `cucumber.plugin=pretty` | 콘솔에 한글 step 텍스트와 통과/실패 여부를 출력한다. |

### 2.4 ScenarioContext — 시나리오 간 상태 공유

```java
@Component
@ScenarioScope
public class ScenarioContext {
    private ExtractableResponse<Response> response;
    private int statusCode;
    private Long categoryId;
    private Long productId;
    private Long optionId;
    private Long senderId;
    private Long receiverId;
    // getter/setter 생략
}
```

**역할:** 하나의 시나리오를 구성하는 여러 Step Definition 클래스 사이에서 상태를 공유한다.

**왜 필요한가?**
- Cucumber에서 하나의 시나리오는 여러 Step Definition 클래스에 걸쳐 실행된다. 예를 들어 `gift.feature`의 Background에서 `CategoryStepDefinitions`가 카테고리를 생성하고, 이후 `GiftStepDefinitions`가 그 `categoryId`를 사용해야 한다.
- Step Definition 클래스끼리는 서로를 참조하지 않으므로, 공유 상태를 담는 중간 객체가 필요하다.

**왜 `@ScenarioScope`인가?**
- 시나리오마다 새 인스턴스가 생성되고, 시나리오가 끝나면 폐기된다.
- 싱글톤이면 이전 시나리오의 상태(ID, 응답)가 다음 시나리오에 누출된다.
- Step Definition 필드에 직접 저장하면 클래스 간 공유가 불가능하다.

### 2.5 CucumberHooks — 시나리오 전처리

```java
public class CucumberHooks {
    @LocalServerPort int port;
    @Autowired DatabaseCleaner databaseCleaner;

    @Before
    public void setUp() {
        RestAssured.port = port;
        databaseCleaner.clear();
    }
}
```

**역할:** 매 시나리오 실행 전 DB를 초기화하고 RestAssured 포트를 설정한다.

**왜 필요한가?**
- 기존 인수 테스트의 `@BeforeEach`와 동일한 역할이다.
- Cucumber에서는 JUnit의 `@BeforeEach` 대신 `io.cucumber.java.Before`를 사용한다.

**왜 별도 클래스인가?**
- Hook은 모든 시나리오에 공통으로 적용되는 횡단 관심사다. Step Definition에 넣으면 특정 도메인과 결합된다.
- DB 클리닝 전략이 바뀌어도 Step Definition은 수정할 필요 없다.

---

## 3. 전체 실행 흐름

```
./gradlew test
  → JUnit Platform이 RunCucumberTest 발견
  → cucumber 엔진 활성화
  → junit-platform.properties에서 glue/features 경로 읽음
  → CucumberSpringConfiguration 발견 → Spring 컨텍스트 1회 기동
  → 시나리오마다:
      1. ScenarioContext 새 인스턴스 생성 (@ScenarioScope)
      2. CucumberHooks.@Before → DB 초기화 + 포트 설정
      3. Background steps 실행 (Given)
      4. Scenario steps 실행 (When → Then)
      5. ScenarioContext 폐기
```

---

## 4. 기존 인수 테스트와의 비교

| 관점 | 기존 (RestAssured 직접) | Cucumber BDD |
|---|---|---|
| 시나리오 표현 | Java 메서드명 (`한글_메서드명`) | `.feature` 파일의 한글 Gherkin |
| 비개발자 가독성 | Java 코드를 읽어야 함 | `.feature` 파일만으로 시나리오 파악 가능 |
| 테스트 전 초기화 | `@BeforeEach` | `io.cucumber.java.Before` (CucumberHooks) |
| Step 간 상태 공유 | 테스트 클래스 필드 | `ScenarioContext` (`@ScenarioScope`) |
| Spring 통합 | `@SpringBootTest` 직접 | `@CucumberContextConfiguration` + `@SpringBootTest` |
| 실행 진입점 | JUnit이 테스트 클래스 직접 발견 | `RunCucumberTest` (`@Suite` + `@IncludeEngines`) |
| 데이터 준비 | Fixture + TestDataInitializer (동일) | Fixture + TestDataInitializer (재사용) |

---

## 5. 프로젝트 내 파일 구조

```
src/test/
├── java/gift/cucumber/
│   ├── CucumberSpringConfiguration.java   # Spring 통합 진입점
│   ├── RunCucumberTest.java               # JUnit Platform Suite 러너
│   ├── ScenarioContext.java               # 시나리오 간 상태 공유
│   ├── CucumberHooks.java                 # 시나리오 전처리 (DB 초기화)
│   └── steps/
│       ├── CategoryStepDefinitions.java   # 카테고리 Step 구현
│       ├── ProductStepDefinitions.java    # 상품 Step 구현
│       └── GiftStepDefinitions.java       # 선물 Step 구현
├── resources/
│   ├── junit-platform.properties          # Cucumber 엔진 설정
│   └── features/
│       ├── category.feature               # 카테고리 시나리오
│       ├── product.feature                # 상품 시나리오
│       └── gift.feature                   # 선물 시나리오
```

---

## 6. 학습 포인트

1. **Cucumber는 Spring을 모른다.** `cucumber-spring` 모듈과 `@CucumberContextConfiguration`이 둘을 연결하는 브릿지 역할을 한다.
2. **JUnit Platform과의 통합에는 명시적 진입점이 필요하다.** `@Suite` + `@IncludeEngines("cucumber")`가 없으면 빌드 도구가 시나리오를 발견하지 못한다.
3. **설정과 코드를 분리한다.** `junit-platform.properties`에 설정을 두면 Java 코드 변경 없이 경로나 플러그인을 조정할 수 있다.
4. **`@ScenarioScope`는 테스트 격리의 핵심이다.** 시나리오마다 새 인스턴스가 생성되어 상태 누출을 방지한다.
5. **횡단 관심사는 Hook으로 분리한다.** DB 초기화 같은 공통 로직을 Step Definition에서 분리해야 변경 영향을 격리할 수 있다.
6. **기존 인프라(Fixture, DatabaseCleaner, TestDataInitializer)는 그대로 재사용한다.** Cucumber 전환 시 테스트 인프라를 새로 만들 필요 없이 Step Definition 계층만 추가하면 된다.
