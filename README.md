# spring-gift-test

## 사전 요구 사항

- Java 21
- Docker

## 실행 방법

### 개발 서버

```bash
./gradlew dockerUp      # 앱 + DB 컨테이너 빌드 및 시작
./gradlew dockerDown     # 컨테이너 + 볼륨 종료
```

- `dockerUp`은 이미지 빌드(`--build`)와 헬스체크 대기(`--wait`)를 포함합니다.
- `dockerDown`은 컨테이너와 익명 볼륨을 함께 제거합니다(`-v`).
- `localhost:8080`으로 앱에 접근할 수 있습니다.

### 단위 테스트

```bash
./gradlew test
```

- Cucumber 인수 테스트를 제외한 모든 테스트를 실행합니다.

### 인수 테스트 (Docker 기반)

앱과 DB 모두 Docker 컨테이너로 실행하여 프로덕션과 동일한 환경에서 E2E 테스트를 수행합니다.

```
Test (Host) → HTTP → localhost:8080  (Docker App)
Test (Host) → JDBC → localhost:5432  (Docker DB)
App (Container) → JDBC → postgres:5432 (Docker DB, Docker 내부 네트워크)
```

```bash
./gradlew dockerUp        # 앱 + DB 컨테이너 빌드 및 시작
./gradlew cucumberTest    # Cucumber 인수 테스트 실행
./gradlew dockerDown      # 컨테이너 + 볼륨 종료
```

- Cucumber `.feature` 파일은 `src/test/resources/features/`에 위치합니다.

### Docker Gradle 태스크

| 태스크 | 설명 |
|--------|------|
| `dockerBuild` | Docker 이미지 빌드 |
| `dockerUp` | 앱 + DB 컨테이너 빌드 및 시작 (헬스체크 대기) |
| `dockerDown` | 컨테이너 + 볼륨 종료 및 제거 |
