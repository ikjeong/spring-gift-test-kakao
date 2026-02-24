# PostgreSQL + Docker Compose 통합

## 1. 개요

모든 환경에서 PostgreSQL을 사용한다. `spring-boot-docker-compose` 모듈이 Spring 컨텍스트 로딩 시 compose 파일을 감지하고, 컨테이너를 시작하고, datasource(URL, username, password)를 자동 구성한다.

| 프로파일 | compose 파일 | 데이터 영속성 | 용도 |
|---|---|---|---|
| `default` | `compose.yaml` | named volume (디스크) | 개발 |
| `test` | `compose-test.yaml` | tmpfs (메모리) | 테스트 |

---

## 2. Gradle 태스크

```groovy
tasks.named('test') {
    useJUnitPlatform {
        excludeEngines 'cucumber'
    }
    exclude '**/cucumber/**'
    systemProperty 'spring.profiles.active', 'test'
}

tasks.register('cucumberTest', Test) {
    description = 'Runs Cucumber acceptance tests with PostgreSQL'
    group = 'verification'
    useJUnitPlatform()
    include '**/RunCucumberTest.class'
    systemProperty 'spring.profiles.active', 'test'
}
```

두 태스크 모두 `test` 프로파일을 사용한다. 차이는 프로파일이 아니라 **실행 대상**이다.

| 태스크 | 실행 대상 | Docker 시작 조건 |
|---|---|---|
| `test` | Cucumber 제외 전부 | Spring 컨텍스트를 로드하는 테스트가 있을 때만 |
| `cucumberTest` | `RunCucumberTest`만 | 항상 (Spring 컨텍스트 로드) |

`test` 태스크에서 Cucumber를 제외할 때 `exclude`(클래스 제외)와 `excludeEngines`(엔진 제외) 두 가지가 모두 필요하다. Cucumber 엔진은 JUnit Platform의 ServiceLoader로 자동 등록되어 `.feature` 파일을 직접 탐색하므로, 클래스 제외만으로는 차단되지 않는다.

프로파일 활성화를 `@ActiveProfiles`가 아닌 Gradle `systemProperty`로 하는 이유: 어노테이션은 Java 코드에 프로파일을 고정하지만, `systemProperty`는 태스크 단위로 제어할 수 있어 코드 변경 없이 프로파일을 조정할 수 있다.

---

## 3. compose 파일

개발과 테스트는 데이터 영속성 요구사항이 다르므로 compose 파일을 분리한다. 개발 환경은 Spring Boot가 `compose.yaml`을 자동 감지하고, 테스트 환경은 `application-test.properties`에서 `compose-test.yaml`을 명시한다.

### compose.yaml (개발)

```yaml
name: gift-dev
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: gift_test
      POSTGRES_USER: gift
      POSTGRES_PASSWORD: gift
    ports:
      - "5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U gift -d gift_test"]
      interval: 2s
      timeout: 5s
      retries: 10
volumes:
  pgdata:
```

named volume(`pgdata`)으로 데이터를 디스크에 영속화한다. 컨테이너를 재생성해도 데이터가 유지되며, `docker compose down -v`로만 삭제된다.

### compose-test.yaml (테스트)

```yaml
name: gift-test
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

tmpfs로 데이터를 메모리에 저장한다. 컨테이너 종료 시 데이터가 사라지며 볼륨이 생성되지 않는다.

### 주요 설정

| 설정 | 의미 |
|---|---|
| `name` | Docker Compose 프로젝트 식별자. `gift-dev`/`gift-test`로 분리하여 개발과 테스트 컨테이너가 충돌하지 않게 한다. |
| `ports: "5432"` | 호스트 랜덤 포트 매핑. `spring-boot-docker-compose`가 실제 포트를 자동 감지하므로 충돌이 없다. |
| `healthcheck` | `spring-boot-docker-compose`는 `healthy` 상태까지 대기 후 datasource를 구성한다. 없으면 DB 초기화 중 `Connection refused` 발생 가능. |

### 볼륨 미지정 시 문제

PostgreSQL 이미지의 Dockerfile에 `VOLUME /var/lib/postgresql/data`가 선언되어 있다. compose 파일에서 해당 경로를 지정하지 않으면 Docker가 익명 볼륨을 자동 생성하고, `docker compose down`으로 제거되지 않아 실행마다 ~50MB씩 누적된다. named volume이나 tmpfs를 명시하면 이를 방지한다.

---

## 4. application-test.properties

```properties
spring.jpa.hibernate.ddl-auto=create-drop
spring.docker.compose.file=compose-test.yaml
spring.docker.compose.skip.in-tests=false
spring.docker.compose.lifecycle-management=start-and-stop
spring.docker.compose.stop.command=down
```

| 속성 | 의미 |
|---|---|
| `ddl-auto=create-drop` | 컨텍스트 시작 시 스키마 생성, 종료 시 삭제. 매 실행마다 깨끗한 스키마로 시작. |
| `compose.file` | 테스트 전용 compose 파일 지정. 미지정 시 `compose.yaml`을 자동 감지. |
| `skip.in-tests=false` | Spring Boot 기본값은 `true`(테스트 시 Docker Compose 건너뜀). 인수 테스트에서 실제 DB가 필요하므로 `false`. |
| `lifecycle-management` | `start-and-stop`: 컨텍스트 시작/종료에 맞춰 컨테이너 자동 관리. |
| `stop.command=down` | `stop`은 컨테이너만 중지, `down`은 컨테이너 + 네트워크까지 제거. 테스트는 `down`으로 완전 정리. |

`spring.datasource.*` 설정이 없는 이유: `spring-boot-docker-compose`가 컨테이너의 환경변수와 포트 매핑을 인스펙션하여 자동 구성한다.

이 파일은 `src/test/resources/`에 위치하여 테스트 클래스패스에만 포함된다.

---

## 5. 실행 흐름

### `./gradlew bootRun`

```
default 프로파일 → compose.yaml 자동 감지 → docker compose up (named volume)
→ healthcheck 대기 → datasource 자동 구성 → ddl-auto=update
→ 앱 종료 시 컨테이너 종료, 데이터는 volume에 보존
```

### `./gradlew cucumberTest`

```
test 프로파일 → compose-test.yaml → docker compose up (tmpfs)
→ healthcheck 대기 → datasource 자동 구성 → ddl-auto=create-drop
→ 시나리오마다 TRUNCATE → 모든 시나리오 완료
→ 스키마 drop → docker compose down → 컨테이너 + 네트워크 정리
```

### `./gradlew test`

```
test 프로파일 설정됨, Cucumber 제외
→ 현재 0개 테스트 → Spring 컨텍스트 미로드 → Docker 미시작
→ 향후 @SpringBootTest 등 추가 시 cucumberTest와 동일하게 Docker 자동 시작
```
