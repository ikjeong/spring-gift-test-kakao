# spring-gift-test

## 사전 요구 사항

- Java 21
- Docker (PostgreSQL 인수 테스트용)

## 실행 방법

### 빌드 및 테스트 (H2)

```bash
./gradlew test
```

- H2 인메모리 DB로 Cucumber BDD 시나리오가 실행됩니다.
- Cucumber `.feature` 파일은 `src/test/resources/features/`에 위치합니다.

### 인수 테스트 (PostgreSQL)

```bash
./gradlew cucumberTest
```

- Docker Compose로 PostgreSQL 컨테이너를 자동 시작합니다.
- 테스트 완료 후 컨테이너가 자동 종료됩니다.