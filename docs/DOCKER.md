# Docker 환경 구성

## 1. 아키텍처 개요

App + DB를 Docker 컨테이너로 실행하여 프로덕션과 동일한 환경에서 개발·테스트한다.

```
┌─── Host ────────────────────────────────────────────────────────┐
│ Test Code ──(HTTP)──→ localhost:8080 ──→ Docker App (:8080)     │
│     │                                        │                  │
│     └──(JDBC)──→ localhost:5432 ──→ Docker PostgreSQL (:5432)   │
│                                        ▲     │                  │
│                                        └─────┘                  │
│                                   (Docker 내부 네트워크)         │
└─────────────────────────────────────────────────────────────────┘
```

### 왜 앱을 컨테이너화하는가?

| 관점 | 임베디드 (이전) | 컨테이너화 (현재) |
|---|---|---|
| 실행 환경 | 개발자 로컬 JVM | Docker 이미지로 고정 |
| 프로덕션과의 차이 | JVM 옵션, 환경변수 차이 가능 | Dockerfile이 프로덕션과 동일 |
| 네트워크 | 루프백 (같은 JVM) | 실제 TCP (Docker 네트워크) |
| 검증 범위 | 코드 로직만 | Dockerfile 빌드, 환경변수 주입, 컨테이너 간 통신까지 |

---

## 2. Dockerfile

```dockerfile
# Builder stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

COPY gradlew build.gradle settings.gradle ./
COPY gradle/ gradle/
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

COPY src/ src/
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Multi-stage build

빌드(JDK + Gradle + 소스)와 실행(JRE + JAR)을 분리하여 최종 이미지를 ~200MB로 줄인다.

| Stage | 베이스 이미지 | 역할 |
|---|---|---|
| Builder | `eclipse-temurin:21-jdk` (~500MB) | JAR 빌드 |
| Runtime | `eclipse-temurin:21-jre-alpine` (~100MB) | JAR 실행 |

### 레이어 캐싱

변경 빈도가 낮은 파일을 먼저 복사하여 캐시 히트율을 높인다.

| 변경 사항 | 캐시 무효화 범위 |
|---|---|
| `src/` 소스코드 수정 | 3단계만 재실행 (의존성 다운로드 캐시) |
| `build.gradle` 의존성 추가 | 2~3단계 재실행 |
| Gradle wrapper 업데이트 | 1~3단계 모두 재실행 |

### 설정별 이유

| 설정 | 이유 |
|---|---|
| `eclipse-temurin` | Eclipse Adoptium 공식 OpenJDK. 무료, LTS 지원 |
| `21-jre-alpine` | 프로젝트 Java 21과 동일. Alpine은 경량 리눅스 |
| `--no-daemon` | Docker 빌드는 일회성. 데몬 유지 불필요, 메모리 절약 |
| `chmod +x gradlew` | Git에서 실행 권한이 보존되지 않을 수 있음 |
| `bootJar` | 실행 가능한 Fat JAR 생성. `build`와 달리 테스트 미실행 |
| `EXPOSE 8080` | 문서화 용도. 실제 포트 매핑은 Docker Compose에서 설정 |
| `ENTRYPOINT` | 컨테이너 시작 시 JAR 실행. 프로파일은 환경변수로 주입 |

---

## 3. compose.yaml

```yaml
name: gift

services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: gift_test
      POSTGRES_USER: gift
      POSTGRES_PASSWORD: gift
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U gift -d gift_test"]
      interval: 2s
      timeout: 5s
      retries: 10

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/gift_test
      SPRING_DATASOURCE_USERNAME: gift
      SPRING_DATASOURCE_PASSWORD: gift
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health || exit 1"]
      interval: 5s
      timeout: 5s
      retries: 10
```

### 서비스별 설정

| 설정 | 값 | 이유 |
|---|---|---|
| `name: gift` | 프로젝트 식별자 | 컨테이너 이름 접두사 (`gift-postgres-1`, `gift-app-1`) |
| `postgres` healthcheck | `pg_isready` | `app`이 `service_healthy` 조건으로 대기하기 위해 필요 |
| `postgres` ports | `5432:5432` (고정) | 호스트 테스트 코드가 JDBC로 직접 접근 (DatabaseCleaner) |
| `app` build | `.` (프로젝트 루트) | Dockerfile 위치. `--build` 시 이미지 자동 빌드 |
| `app` ports | `8080:8080` (고정) | 호스트 테스트 → 앱 HTTP 접근, 개발 시 브라우저 접근 |
| `SPRING_DATASOURCE_URL` | `postgres:5432` | Docker 내부 DNS가 서비스 이름을 IP로 해석 |
| `depends_on` | `service_healthy` | DB ready 상태에서만 앱 시작. 없으면 `Connection refused` |
| `app` healthcheck | `wget .../actuator/health` | `--wait` 플래그가 healthy 대기. Alpine에 `curl` 없고 `wget`은 BusyBox에 포함 |

### 익명 볼륨

PostgreSQL 이미지의 `VOLUME /var/lib/postgresql/data` 선언으로 익명 볼륨이 자동 생성된다.

- `docker compose down`만으로는 삭제되지 않음
- `docker compose down -v`로 컨테이너와 함께 삭제
- `dockerDown` 태스크에 `-v`를 포함하여 매번 정리

개발 시 데이터를 유지하려면 `dockerDown` 대신 컨테이너를 stop만 하면 된다.

---

## 4. 프로파일 설계

2개 프로파일 체계. 앱 컨테이너는 `default` + 환경변수 오버라이드로 동작한다.

| 프로파일 | 파일 | 실행 주체 | 용도 |
|---|---|---|---|
| `default` | `application.properties` | Docker 앱 컨테이너 | 개발·컨테이너 내부 앱 |
| `e2e` | `application-e2e.properties` | 테스트 JVM | E2E 인수 테스트 호스트 |

### application-e2e.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/gift_test
spring.datasource.username=gift
spring.datasource.password=gift
spring.jpa.hibernate.ddl-auto=none
```

| 속성 | 값 | 이유 |
|---|---|---|
| `datasource.url` | `localhost:5432` | 호스트에서 Docker DB의 고정 포트로 연결 |
| `ddl-auto` | `none` | 스키마는 앱 컨테이너가 `update`로 관리. 테스트가 스키마를 건드리면 앱과 충돌 |

### DDL 역할 분담

| 환경 | ddl-auto | 이유 |
|---|---|---|
| 앱 컨테이너 (`default`) | `update` | Entity 기반으로 스키마 생성/갱신 |
| E2E 테스트 호스트 (`e2e`) | `none` | 앱에 위임. 테스트는 데이터만 TRUNCATE |

---

## 5. Gradle 태스크

### 테스트 태스크

```groovy
tasks.named('test') {
    useJUnitPlatform {
        excludeEngines 'cucumber'
    }
    exclude '**/cucumber/**'
}

tasks.register('cucumberTest', Test) {
    description = 'Runs Cucumber acceptance tests with PostgreSQL'
    group = 'verification'
    useJUnitPlatform()
    include '**/RunCucumberTest.class'
}
```

| 태스크 | 프로파일 | 실행 대상 | Docker 필요 |
|---|---|---|---|
| `test` | (없음) | Cucumber 제외 전부 | 불필요 |
| `cucumberTest` | `e2e` (`@ActiveProfiles`) | `RunCucumberTest`만 | `dockerUp` 사전 실행 |

`test`에서 Cucumber를 제외할 때 `exclude`(클래스)와 `excludeEngines`(엔진) 두 가지가 모두 필요하다. Cucumber 엔진은 ServiceLoader로 자동 등록되어 `.feature` 파일을 직접 탐색하므로, 클래스 제외만으로는 차단되지 않는다.

프로파일은 `CucumberSpringConfiguration`의 `@ActiveProfiles("e2e")`로 활성화한다. Cucumber 테스트는 항상 `e2e` 프로파일을 사용하므로, 런타임에 프로파일을 바꿀 필요가 없다. 테스트 코드에 명시하면 IDE에서 직접 실행해도 프로파일이 자동 적용된다.

### Docker 태스크

```groovy
tasks.register('dockerBuild', Exec) {
    commandLine 'docker', 'build', '-t', 'gift-app', '.'
}
tasks.register('dockerUp', Exec) {
    commandLine 'docker-compose', 'up', '-d', '--build', '--wait'
}
tasks.register('dockerDown', Exec) {
    commandLine 'docker-compose', 'down', '-v'
}
```

| 플래그 | 의미 |
|---|---|
| `-d` | Detached 모드. 백그라운드 실행 |
| `--build` | 실행 전 이미지 항상 재빌드. 소스코드 변경 반영 보장 |
| `--wait` | 모든 서비스 healthcheck가 healthy일 때까지 대기 |
| `-v` | 익명 볼륨도 함께 삭제. 누적 방지 |

---

## 6. 실행 흐름

### `./gradlew dockerUp`

```
docker-compose up -d --build --wait
→ Dockerfile builder: Gradle로 bootJar 빌드
→ Dockerfile runtime: JAR 복사, 경량 이미지 생성
→ postgres 시작 → healthcheck (pg_isready) 대기
→ postgres healthy → app 시작
→ app: default 프로파일 + 환경변수 오버라이드
→ app: ddl-auto=update → 스키마 생성/갱신
→ app healthcheck (/actuator/health) → healthy
→ --wait 충족 → 반환
```

### `./gradlew cucumberTest`

```
e2e 프로파일 → application-e2e.properties 로드
→ Spring 컨텍스트 (webEnvironment=NONE)
→ datasource: localhost:5432 → Docker PostgreSQL에 JDBC 연결
→ 시나리오마다 TRUNCATE → localhost:8080의 Docker 앱에 HTTP 요청
→ 모든 시나리오 완료
(사전: dockerUp, 사후: dockerDown 필요)
```

### `./gradlew dockerDown`

```
docker-compose down -v
→ app/postgres 컨테이너 중지·제거
→ 익명 볼륨 삭제
→ Docker 네트워크 제거
```

### `./gradlew test`

```
프로파일 없음, Cucumber 제외
→ 현재 0개 테스트
→ 순수 단위 테스트 전용 (Docker 불필요)
```

---

## 7. 대안 분석

### 컨테이너 관리 방식

| 방식 | 장점 | 단점 |
|---|---|---|
| **현재: Docker Compose + Gradle Exec** | 각 단계가 명시적. compose 파일로 인프라 구성 가시적 | 3개 명령어 순서 실행 필요. 실패 시 수동 정리 |
| Testcontainers | 라이프사이클 자동 관리. 실패 시에도 자동 정리. 테스트 코드만으로 인프라 구성 완결 | 별도 라이브러리 의존성 추가 필요 |
| 임베디드 모드 (RANDOM_PORT) | Dockerfile 불필요, 설정 단순, 디버깅 용이 | Dockerfile 빌드·컨테이너 간 통신 검증 불가 |

**현재 선택:** Docker Compose + Gradle Exec. Docker의 각 단계를 직접 다뤄보기 위해 선택했다.

**개선 포인트:** Testcontainers가 더 나은 선택이다. 3개 명령어를 순서대로 실행해야 하는 번거로움과 실패 시 수동 정리 문제를 모두 해결한다. Docker Compose 구성에 익숙해졌다면 전환을 고려할 것.

### 프로파일 활성화 방식

| 방식 | 장점 | 단점 |
|---|---|---|
| **현재: `@ActiveProfiles`** | 코드에서 명시적. IDE에서 자동 적용 | 프로파일 변경 시 Java 재컴파일 필요 |
| Gradle `systemProperty` | 코드 변경 없이 태스크 단위 제어 | IDE 직접 실행 시 별도 설정 필요 |

**선택 근거:** Cucumber 테스트는 항상 `e2e` 프로파일을 사용한다. 런타임에 프로파일을 전환할 필요가 없으므로, 테스트 코드에 명시하여 IDE에서도 별도 설정 없이 동작하는 `@ActiveProfiles`가 적합하다.

---

## .dockerignore

```
.git
.gradle
build/
.idea
.claude
docs/
*.iml
```

빌드 컨텍스트에서 불필요한 파일을 제외하여 전송 시간을 단축한다. `.git` 디렉토리만으로 수십 초가 걸릴 수 있다.
