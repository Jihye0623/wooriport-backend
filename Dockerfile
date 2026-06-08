# syntax=docker/dockerfile:1

# ── build stage ──────────────────────────────────────────────
# gradle 공식 이미지 사용 = gradlew 배포본(130MB) 다운로드 회피.
# (NAT 환경에서 wrapper 다운로드가 read-timeout 으로 실패하던 문제 해결)
# 테스트는 Testcontainers(도커 필요)라 빌드 중엔 생략.
FROM gradle:8.14.4-jdk21 AS build
WORKDIR /app
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY src ./src
RUN gradle bootJar -x test --no-daemon

# ── run stage ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
# 환경변수(SPRING_DATASOURCE_URL, DB_USERNAME, REDIS_HOST, KAFKA_BOOTSTRAP_SERVERS,
# FLASK_ML_URL, JWT_SECRET 등)는 docker run --env-file 로 주입.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
