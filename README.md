# spring-gift-test

## 사전 요구 사항

- Java 21
- Docker

## 실행 방법

### 개발 서버

```bash
./gradlew bootRun
```

- Docker Compose로 PostgreSQL 컨테이너를 자동 시작합니다.
- 앱 종료 시 컨테이너도 함께 종료되지만, 데이터는 named volume에 보존됩니다.

### 테스트

```bash
./gradlew test
```

- Cucumber 인수 테스트를 제외한 모든 테스트를 실행합니다.
- Spring 컨텍스트를 로드하는 테스트가 있으면 Docker Compose로 PostgreSQL을 자동 시작합니다.

### 인수 테스트

```bash
./gradlew cucumberTest
```

- Docker Compose로 PostgreSQL 컨테이너를 자동 시작합니다.
- 테스트 완료 후 컨테이너가 자동 종료됩니다.
- Cucumber `.feature` 파일은 `src/test/resources/features/`에 위치합니다.
