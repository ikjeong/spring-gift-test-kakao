# PostgreSQL + Docker Compose 통합 학습

## 1. 개요

기존에는 H2 인메모리 DB로 Cucumber 인수 테스트를 실행했다. H2는 빠르고 설정이 간편하지만 운영 환경(PostgreSQL)과 SQL 방언, 트랜잭션 동작, 타입 시스템이 다르다. 이 차이 때문에 H2에서 통과한 테스트가 운영 DB에서 실패하는 문제가 발생할 수 있다.

이를 해결하기 위해 Spring Boot의 `spring-boot-docker-compose` 모듈을 도입했다. 이 모듈은 Spring 컨텍스트가 로딩될 때 프로젝트 루트의 `compose.yaml`을 감지하여 Docker Compose를 자동으로 시작하고, 실행된 컨테이너에서 datasource 정보(URL, username, password)를 자동으로 추출하여 Spring에 주입한다. 개발자가 `spring.datasource.url`을 직접 설정할 필요가 없다.

**프로파일 전략:**
- `default` (개발/기본 테스트): H2 유지 — 기존 동작 변경 없음
- `test` (인수 테스트): PostgreSQL via Docker Compose

---

## 2. 구성요소

### 2.1 build.gradle — 의존성과 Gradle 태스크

```groovy
dependencies {
    runtimeOnly 'com.h2database:h2'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-docker-compose'
}

tasks.register('cucumberTest', Test) {
    description = 'Runs Cucumber acceptance tests with PostgreSQL'
    group = 'verification'
    useJUnitPlatform()
    include '**/RunCucumberTest.class'
    systemProperty 'spring.profiles.active', 'test'
}
```

#### 의존성 설명

| 의존성 | scope | 역할 |
|---|---|---|
| `com.h2database:h2` | `runtimeOnly` | H2 인메모리 DB 드라이버. `default` 프로파일에서 사용한다. `runtimeOnly`인 이유는 컴파일 시점에 H2 API를 직접 참조하지 않고, JPA/JDBC가 런타임에 드라이버를 로드하기 때문이다. |
| `org.postgresql:postgresql` | `runtimeOnly` | PostgreSQL JDBC 드라이버. `test` 프로파일에서 Docker Compose로 띄운 PostgreSQL에 연결할 때 사용한다. H2와 동일한 이유로 `runtimeOnly`이다. |
| `spring-boot-docker-compose` | `testImplementation` | Spring Boot의 Docker Compose 통합 모듈. **`testImplementation`으로 지정한 이유:** `implementation`이나 `runtimeOnly`로 넣으면 `bootRun`(로컬 개발 서버) 실행 시에도 Docker Compose를 찾으려고 시도한다. 테스트에서만 Docker가 필요하므로 `testImplementation`으로 범위를 제한한다. |

#### cucumberTest 태스크 설명

| 설정 | 의미 |
|---|---|
| `tasks.register('cucumberTest', Test)` | 기존 `test` 태스크와 별도로 새 테스트 태스크를 등록한다. `Test` 타입을 상속하므로 Gradle의 테스트 실행 인프라(리포트, 병렬 실행 등)를 그대로 사용한다. |
| `group = 'verification'` | `./gradlew tasks`에서 verification 카테고리에 표시된다. `test`, `check` 등과 같은 그룹이다. |
| `useJUnitPlatform()` | JUnit Platform 기반으로 테스트를 실행한다. Cucumber의 `cucumber` 엔진이 JUnit Platform에 등록되어 있으므로 이 설정이 필요하다. |
| `include '**/RunCucumberTest.class'` | Cucumber 시나리오의 진입점인 `RunCucumberTest` 클래스만 실행 대상으로 포함한다. 다른 단위 테스트가 있어도 이 태스크에서는 실행되지 않는다. |
| `systemProperty 'spring.profiles.active', 'test'` | JVM 시스템 프로퍼티로 `test` 프로파일을 활성화한다. 이로 인해 `application-test.properties`가 로드되고, Docker Compose + PostgreSQL 환경이 구성된다. **`@ActiveProfiles("test")`를 Java 코드에 쓰지 않은 이유:** 어노테이션으로 프로파일을 지정하면 `./gradlew test`에서도 `test` 프로파일이 활성화되어 Docker Compose를 시도한다. Gradle 태스크의 `systemProperty`로 프로파일을 주입하면 `test` 태스크(H2)와 `cucumberTest` 태스크(PostgreSQL)를 깔끔하게 분리할 수 있다. |

**왜 두 태스크로 분리했는가?**
- `./gradlew test`: Docker 없이 H2로 빠르게 실행. CI에서 Docker를 쓸 수 없는 환경이나, 로컬에서 빠른 피드백이 필요할 때 사용한다.
- `./gradlew cucumberTest`: Docker Compose로 PostgreSQL을 띄워 운영 환경에 가까운 DB에서 검증한다. 배포 전 최종 검증에 적합하다.

---

### 2.2 compose.yaml — PostgreSQL 컨테이너 정의

```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: gift_test
      POSTGRES_USER: gift
      POSTGRES_PASSWORD: gift
    ports:
      - "5432"
    tmpfs:
      - /var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U gift -d gift_test"]
      interval: 2s
      timeout: 5s
      retries: 10
```

| 설정 | 값 | 의미 |
|---|---|---|
| `image` | `postgres:17` | PostgreSQL 17 공식 Docker 이미지를 사용한다. 메이저 버전만 지정하여 패치 업데이트를 자동으로 받는다. |
| `POSTGRES_DB` | `gift_test` | 컨테이너 시작 시 자동으로 생성되는 데이터베이스 이름. `spring-boot-docker-compose`가 이 값을 감지하여 JDBC URL의 데이터베이스 부분에 사용한다. |
| `POSTGRES_USER` | `gift` | DB 사용자명. `spring-boot-docker-compose`가 자동으로 `spring.datasource.username`에 매핑한다. |
| `POSTGRES_PASSWORD` | `gift` | DB 비밀번호. 마찬가지로 `spring.datasource.password`에 자동 매핑된다. |
| `ports` | `"5432"` | 컨테이너 내부 포트 5432를 **호스트의 랜덤 포트**에 매핑한다. `"5432:5432"`로 쓰면 호스트의 5432를 고정으로 사용하므로 로컬에 이미 PostgreSQL이 실행 중이면 충돌한다. 랜덤 매핑으로 이를 방지한다. `spring-boot-docker-compose`는 실행 중인 컨테이너의 실제 매핑 포트를 감지하여 JDBC URL을 구성한다. |
| `tmpfs` | `/var/lib/postgresql/data` | PostgreSQL의 데이터 디렉터리를 메모리(tmpfs)에 마운트한다. 아래 "왜 tmpfs가 필요한가?" 절에서 상세히 설명한다. |
| `healthcheck.test` | `pg_isready -U gift -d gift_test` | PostgreSQL이 연결을 받을 준비가 되었는지 확인하는 명령어. `pg_isready`는 PostgreSQL에 내장된 유틸리티로, 서버가 커넥션을 수락할 수 있는 상태인지 검사한다. |
| `healthcheck.interval` | `2s` | 2초마다 healthcheck를 실행한다. |
| `healthcheck.timeout` | `5s` | 개별 healthcheck 명령어의 타임아웃. 5초 내에 응답하지 않으면 해당 시도는 실패로 간주한다. |
| `healthcheck.retries` | `10` | 최대 10회 재시도. 10회 모두 실패하면 컨테이너가 `unhealthy` 상태가 된다. 2초 간격 × 10회 = 최대 약 20초 대기한다. |

**왜 tmpfs가 필요한가?**

PostgreSQL 공식 Docker 이미지의 Dockerfile에는 `VOLUME /var/lib/postgresql/data`가 선언되어 있다. 이 선언 때문에 Docker는 컨테이너가 생성될 때마다 **익명 볼륨(anonymous volume)**을 자동으로 만든다.

문제는 `docker compose down`이 컨테이너와 네트워크는 제거하지만 **볼륨은 제거하지 않는다**는 점이다. 볼륨까지 제거하려면 `docker compose down -v` 플래그가 필요한데, Spring Boot의 `spring.docker.compose.stop.command=down`은 `-v` 플래그를 지원하지 않는다. 결과적으로 테스트를 실행할 때마다 약 50MB의 익명 볼륨이 누적된다.

`tmpfs`로 마운트하면 Docker의 `VOLUME` 선언을 오버라이드하여 데이터를 메모리에 저장한다. 볼륨 자체가 생성되지 않으므로 누적 문제가 원천 차단된다. 부수적으로 디스크 I/O 대신 메모리 I/O를 사용하므로 테스트 속도도 향상된다. 테스트 환경에서는 데이터 영속성이 불필요하므로 tmpfs가 적합하다.

**healthcheck가 왜 중요한가?**

`spring-boot-docker-compose`는 컨테이너가 `healthy` 상태가 될 때까지 기다린 후에 datasource를 구성한다. healthcheck가 없으면 컨테이너가 `running` 상태이지만 PostgreSQL이 아직 초기화 중일 때 연결을 시도하여 `Connection refused` 에러가 발생할 수 있다.

**`spring-boot-docker-compose`의 자동 구성 과정:**

1. 프로젝트 루트에서 `compose.yaml` (또는 `docker-compose.yaml`) 파일을 감지
2. `docker compose up`을 실행하여 컨테이너 시작
3. healthcheck로 컨테이너가 `healthy` 상태가 될 때까지 대기
4. 실행 중인 컨테이너의 환경변수(`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`)와 포트 매핑을 읽음
5. 이 정보로 `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password`를 자동 구성
6. 개발자가 `application.properties`에 datasource를 직접 설정할 필요가 없음

---

### 2.3 application-test.properties — test 프로파일 설정

```properties
spring.jpa.hibernate.ddl-auto=create-drop
spring.docker.compose.skip.in-tests=false
spring.docker.compose.lifecycle-management=start-and-stop
spring.docker.compose.stop.command=down
```

| 속성 | 값 | 의미 |
|---|---|---|
| `spring.jpa.hibernate.ddl-auto` | `create-drop` | Spring 컨텍스트 시작 시 JPA 엔티티 매핑을 기반으로 테이블을 **생성**(create)하고, 컨텍스트 종료 시 **삭제**(drop)한다. 매 테스트 실행마다 깨끗한 스키마로 시작하므로 스키마 변경이 누적되지 않는다. 다른 옵션: `create`(시작 시 생성만, 종료 시 유지), `update`(기존 스키마에 변경분만 반영), `validate`(스키마 검증만, 변경 안 함), `none`(아무것도 안 함). 운영 환경에서는 `validate`나 `none`을 쓰고 Flyway 같은 마이그레이션 도구로 스키마를 관리하지만, 테스트에서는 `create-drop`이 적합하다. |
| `spring.docker.compose.skip.in-tests` | `false` | **Spring Boot의 기본값은 `true`이다.** 즉, 테스트 실행 시 Docker Compose 통합을 건너뛴다. Spring Boot가 이렇게 기본값을 정한 이유는, 대부분의 단위 테스트나 슬라이스 테스트(`@WebMvcTest`, `@DataJpaTest`)에서는 Docker 컨테이너가 불필요하기 때문이다. 우리는 인수 테스트에서 실제 PostgreSQL이 필요하므로 `false`로 설정하여 Docker Compose를 활성화한다. |
| `spring.docker.compose.lifecycle-management` | `start-and-stop` | Docker Compose 컨테이너의 생명주기를 Spring 컨텍스트와 동기화한다. 가능한 값: `none`(아무것도 안 함), `start-only`(시작만, 종료는 수동), `start-and-stop`(시작과 종료 모두 자동). `start-and-stop`으로 설정하면 테스트 종료 시 컨테이너가 자동으로 정리되어 좀비 컨테이너가 남지 않는다. |
| `spring.docker.compose.stop.command` | `down` | 컨테이너 종료 시 실행할 Docker Compose 명령어. `stop`은 컨테이너만 중지하고 네트워크/볼륨은 유지한다. `down`은 컨테이너 + 네트워크 + 익명 볼륨까지 모두 제거한다. 테스트 환경에서는 매 실행마다 완전히 깨끗한 상태에서 시작해야 하므로 `down`을 사용한다. |

**왜 `spring.datasource.url`, `username`, `password`가 없는가?**

`spring-boot-docker-compose`가 Docker Compose의 실행 중인 컨테이너를 인스펙션하여 자동으로 구성한다. 컨테이너의 환경변수(`POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`)와 포트 매핑에서 JDBC URL을 생성한다. 예를 들어 호스트 포트가 32768로 매핑되었다면 `jdbc:postgresql://localhost:32768/gift_test`가 자동으로 설정된다.

**이 파일이 `src/test/resources/`에 위치하는 이유:**

Spring Boot는 프로파일 이름으로 설정 파일을 찾는다. `test` 프로파일이 활성화되면 `application-test.properties`를 로드한다. `src/test/resources/`에 두면 테스트 클래스패스에만 포함되어 `bootRun`(메인 실행)에서는 절대 로드되지 않는다.

---

### 2.4 DatabaseCleaner — 다중 DB 지원

```java
@Component
public class DatabaseCleaner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    public void clear() {
        if ("PostgreSQL".equalsIgnoreCase(getDatabaseProductName())) {
            jdbcTemplate.execute(
                "TRUNCATE TABLE wish, option, product, member, category RESTART IDENTITY CASCADE"
            );
        } else {
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
            jdbcTemplate.execute("TRUNCATE TABLE wish");
            jdbcTemplate.execute("TRUNCATE TABLE option");
            jdbcTemplate.execute("TRUNCATE TABLE product");
            jdbcTemplate.execute("TRUNCATE TABLE member");
            jdbcTemplate.execute("TRUNCATE TABLE category");
            jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
        }
    }

    private String getDatabaseProductName() {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
```

**왜 DB 유형을 감지하는가?**

H2와 PostgreSQL의 TRUNCATE 문법이 다르기 때문이다. `./gradlew test`(H2)와 `./gradlew cucumberTest`(PostgreSQL) 모두에서 동일한 `DatabaseCleaner`가 사용되므로, 런타임에 연결된 DB 종류를 감지하여 적절한 SQL을 실행해야 한다.

#### PostgreSQL 분기

```sql
TRUNCATE TABLE wish, option, product, member, category RESTART IDENTITY CASCADE
```

| 절 | 의미 |
|---|---|
| `TRUNCATE TABLE wish, option, ...` | PostgreSQL은 하나의 TRUNCATE 문에 여러 테이블을 나열할 수 있다. 나열된 모든 테이블이 하나의 작업으로 동시에 비워진다. |
| `RESTART IDENTITY` | 각 테이블에 연결된 시퀀스(auto-increment 값)를 초기값으로 리셋한다. 이 옵션이 없으면 다음 테스트에서 ID가 이전 테스트의 마지막 값에서 이어진다. 시나리오마다 ID가 1부터 시작해야 테스트 데이터의 예측이 가능하다. |
| `CASCADE` | TRUNCATE 대상 테이블을 FK로 참조하는 다른 테이블도 함께 비운다. 예를 들어 `product`를 TRUNCATE하면 `product`를 FK로 참조하는 `wish`와 `option`도 함께 비워진다. 이 옵션이 없으면 FK 제약조건 위반 에러가 발생할 수 있다. |

#### H2 분기

```sql
SET REFERENTIAL_INTEGRITY FALSE
TRUNCATE TABLE wish
TRUNCATE TABLE option
...
SET REFERENTIAL_INTEGRITY TRUE
```

| 문 | 의미 |
|---|---|
| `SET REFERENTIAL_INTEGRITY FALSE` | H2 전용 명령어. FK 제약조건 검사를 일시 비활성화한다. PostgreSQL의 `CASCADE`에 대응하는 H2의 방식이다. |
| `TRUNCATE TABLE <name>` (개별) | H2는 하나의 TRUNCATE 문에 여러 테이블을 나열할 수 없다. 테이블마다 별도 문장을 실행해야 한다. |
| `SET REFERENTIAL_INTEGRITY TRUE` | FK 제약조건 검사를 다시 활성화한다. TRUNCATE가 끝난 후 반드시 복원해야 이후 테스트에서 데이터 무결성이 보장된다. |

#### DB 감지 방식

```java
private String getDatabaseProductName() {
    try (var connection = dataSource.getConnection()) {
        return connection.getMetaData().getDatabaseProductName();
    }
}
```

`DataSource`에서 커넥션을 획득하고, JDBC 표준 API인 `DatabaseMetaData.getDatabaseProductName()`으로 DB 제품명을 반환받는다. H2는 `"H2"`, PostgreSQL은 `"PostgreSQL"`을 반환한다. 이 방식은 프로파일이나 프로퍼티에 의존하지 않고, 실제 연결된 DB를 직접 확인하므로 가장 정확하다.

**다른 방식과의 비교:**

| 방식 | 장점 | 단점 |
|---|---|---|
| `DatabaseMetaData` (채택) | 실제 DB를 직접 확인. 프로파일 설정과 무관하게 정확하다. | 커넥션을 한 번 획득해야 한다 (HikariCP 풀에서 가져오므로 비용 미미). |
| `Environment.getActiveProfiles()` | 커넥션 불필요 | 프로파일 이름에 의존. 프로파일 이름이 바뀌면 함께 수정해야 한다. |
| `@Profile`로 빈 분리 | 각 구현이 명확히 분리됨 | 클래스가 2개로 늘어남. 현재 규모에서는 과도한 설계. |

---

### 2.5 CucumberSpringConfiguration — 프로파일 설정의 부재

```java
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CucumberSpringConfiguration {
}
```

**왜 `@ActiveProfiles("test")`를 넣지 않았는가?**

처음 계획에서는 이 클래스에 `@ActiveProfiles("test")`를 추가하려 했다. 하지만 실제 적용 시 다음 문제가 발생했다:

1. `@ActiveProfiles`는 Spring TestContext Framework의 어노테이션으로, 이 어노테이션이 붙은 설정 클래스를 사용하는 **모든 테스트**에 해당 프로파일이 적용된다.
2. `./gradlew test`도 동일한 `CucumberSpringConfiguration`을 사용하므로, `test` 프로파일이 활성화된다.
3. `test` 프로파일에는 `spring.docker.compose.skip.in-tests=false`가 설정되어 있어 Docker Compose를 시작하려 한다.
4. `./gradlew test`는 Docker 없이 H2로 빠르게 실행하려는 용도인데, Docker Compose를 찾다가 실패한다.

**해결:** 프로파일 활성화를 Java 코드가 아닌 Gradle 태스크의 `systemProperty`에서만 수행한다. 이렇게 하면:
- `./gradlew test` → `systemProperty` 없음 → `default` 프로파일 → H2
- `./gradlew cucumberTest` → `systemProperty 'spring.profiles.active', 'test'` → `test` 프로파일 → PostgreSQL

---

## 3. 전체 실행 흐름

### `./gradlew cucumberTest` (PostgreSQL)

```
./gradlew cucumberTest
  → Gradle이 JVM 시스템 프로퍼티 -Dspring.profiles.active=test 설정
  → RunCucumberTest.class만 실행 대상으로 포함
  → JUnit Platform이 cucumber 엔진 활성화
  → CucumberSpringConfiguration 발견 → Spring 컨텍스트 로딩 시작
  → test 프로파일 활성화 → application-test.properties 로드
  → spring-boot-docker-compose가 compose.yaml 감지
  → docker compose up 실행 → PostgreSQL 컨테이너 시작
  → healthcheck로 PostgreSQL이 ready 상태가 될 때까지 대기
  → 컨테이너의 환경변수 + 포트 매핑에서 datasource 자동 구성
  → Hibernate가 JPA 엔티티로부터 스키마 create (create-drop)
  → 시나리오마다:
      1. CucumberHooks.@Before → DatabaseCleaner.clear() (PostgreSQL TRUNCATE)
      2. Background steps 실행 (Given)
      3. Scenario steps 실행 (When → Then)
  → 모든 시나리오 완료
  → Spring 컨텍스트 종료 → Hibernate가 스키마 drop
  → spring-boot-docker-compose가 docker compose down 실행
  → 컨테이너 + 네트워크 정리 완료 (tmpfs이므로 볼륨 미생성)
```

### `./gradlew test` (H2)

```
./gradlew test
  → systemProperty 없음 → default 프로파일
  → spring-boot-docker-compose가 클래스패스에 있지만:
      skip.in-tests의 기본값이 true → Docker Compose 스킵
  → H2 자동 구성 (Spring Boot의 기본 DataSource 자동 구성)
  → 시나리오마다:
      1. DatabaseCleaner.clear() → H2 감지 → H2 TRUNCATE 실행
  → 테스트 완료 → H2 인메모리 DB 자동 폐기
```

---

## 4. 변경이 불필요했던 파일과 그 이유

| 파일/영역 | 왜 변경이 불필요한가 |
|---|---|
| **JPA 엔티티** (`@GeneratedValue(strategy = GenerationType.IDENTITY)`) | `IDENTITY` 전략은 DB의 auto-increment/serial 기능에 위임한다. H2의 `IDENTITY` 타입과 PostgreSQL의 `GENERATED BY DEFAULT AS IDENTITY` 모두 이 전략을 지원한다. |
| **TestDataInitializer** (`SimpleJdbcInsert`) | `SimpleJdbcInsert`는 JDBC 메타데이터를 읽어 INSERT 문을 생성하므로 DB에 독립적이다. 테이블명과 컬럼명만 맞으면 어떤 DB에서든 동작한다. |
| **Step Definitions / Feature Files** | API 레이어(HTTP 요청/응답)를 통해 테스트하므로, 그 뒤에 어떤 DB가 있는지는 Step Definition이 알 필요 없다. |
| **application.properties (main)** | 개발 환경(H2)의 설정을 그대로 유지한다. `test` 프로파일의 설정은 별도의 `application-test.properties`에 분리되어 있다. |
| **Fixture 클래스** | Fixture는 도메인 객체를 생성하는 역할만 담당한다. DB에 저장하는 방법은 `TestDataInitializer`에 위임되어 있으므로, DB가 바뀌어도 Fixture는 변경할 필요가 없다. |

---

## 5. 프로젝트 내 파일 구조 (Docker Compose 관련)

```
project-root/
├── compose.yaml                                 # Docker Compose 정의 (PostgreSQL)
├── build.gradle                                 # 의존성 + cucumberTest 태스크
└── src/test/
    ├── java/gift/support/
    │   └── DatabaseCleaner.java                 # H2/PostgreSQL 이중 지원
    └── resources/
        └── application-test.properties          # test 프로파일 설정
```

---

## 6. 학습 포인트

1. **`spring-boot-docker-compose`는 datasource를 자동 구성한다.** `compose.yaml`의 환경변수와 포트 매핑을 인스펙션하여 `spring.datasource.url/username/password`를 자동으로 주입한다. 수동 설정이 불필요하다.
2. **테스트에서 Docker Compose를 사용하려면 `skip.in-tests=false`가 필요하다.** Spring Boot는 기본적으로 테스트 시 Docker Compose를 건너뛴다. 이는 대부분의 테스트(단위, 슬라이스)에서 Docker가 불필요하기 때문이다.
3. **포트 충돌은 랜덤 매핑으로 방지한다.** `"5432"`(호스트 랜덤)와 `"5432:5432"`(호스트 고정)의 차이를 이해해야 한다. 랜덤 매핑하면 `spring-boot-docker-compose`가 실제 포트를 자동으로 감지한다.
4. **healthcheck는 연결 안정성의 핵심이다.** 컨테이너가 `running`이어도 DB가 아직 초기화 중일 수 있다. `pg_isready`로 실제 연결 가능 여부를 확인한 후에야 Spring이 datasource를 구성한다.
5. **프로파일 활성화 방식은 영향 범위를 고려해야 한다.** `@ActiveProfiles`는 해당 설정 클래스를 사용하는 모든 테스트에 적용된다. Gradle `systemProperty`는 특정 태스크에만 적용된다. 두 태스크(H2/PostgreSQL)가 같은 테스트 클래스를 공유할 때는 `systemProperty` 방식이 적합하다.
6. **DatabaseCleaner는 연결된 DB를 직접 확인하는 것이 가장 안전하다.** `DatabaseMetaData.getDatabaseProductName()`은 프로파일 이름이나 설정 값에 의존하지 않고, 실제 JDBC 커넥션에서 DB 제품명을 읽는다.
7. **`TRUNCATE ... RESTART IDENTITY CASCADE`로 테스트 격리를 보장한다.** `RESTART IDENTITY`는 시퀀스를 초기화하여 매 시나리오마다 ID가 1부터 시작하게 한다. `CASCADE`는 FK 의존성을 자동으로 처리하여 삭제 순서를 신경 쓸 필요가 없다.
8. **`lifecycle-management=start-and-stop` + `stop.command=down`으로 좀비 컨테이너를 방지한다.** `stop`은 컨테이너만 중지하고, `down`은 네트워크까지 제거한다. 테스트 환경에서는 `down`으로 완전히 정리해야 다음 실행에 영향을 주지 않는다.
9. **`tmpfs`로 익명 볼륨 누적을 방지한다.** PostgreSQL 공식 이미지는 Dockerfile에 `VOLUME /var/lib/postgresql/data`를 선언하여 컨테이너 생성 시마다 익명 볼륨을 만든다. `docker compose down`은 볼륨을 제거하지 않고(`-v` 플래그 필요), Spring Boot의 `stop.command=down`은 `-v`를 지원하지 않는다. `tmpfs`로 해당 경로를 오버라이드하면 볼륨 자체가 생성되지 않아 문제가 원천 차단되며, 메모리 I/O로 테스트 속도도 향상된다.
