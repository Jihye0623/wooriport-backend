# syntax=docker/dockerfile:1

# ── build stage ──────────────────────────────────────────────
# Gradle Wrapper로 boot jar 빌드. 테스트는 Testcontainers(도커 필요)라 빌드 중엔 생략.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# 의존성 캐시 레이어 (소스보다 먼저 복사)
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

COPY src ./src
# bootJar 만 실행 → plain jar 안 생겨서 산출물이 하나로 깔끔
RUN ./gradlew bootJar -x test --no-daemon

# ── run stage ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
# 환경변수(SPRING_DATASOURCE_URL, DB_USERNAME, REDIS_HOST, KAFKA_BOOTSTRAP_SERVERS,
# FLASK_ML_URL, JWT_SECRET 등)는 docker run -e / compose env 로 주입.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
