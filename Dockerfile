# ===== 1) 빌드 스테이지 =====
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle wrapper·설정을 먼저 복사해 의존성 레이어를 캐시 (소스만 바뀌면 재다운로드 X)
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# 소스 복사 후 실행 가능한 부트 JAR 생성 (테스트는 CI에서 별도 수행하므로 제외)
COPY src src
RUN ./gradlew clean bootJar --no-daemon -x test

# ===== 2) 런타임 스테이지 =====
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# 보안: 비루트 사용자로 실행
RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar

# 컨테이너 메모리 한도에 맞춰 힙을 자동 조정 (1GB 인스턴스 대응)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0"

EXPOSE 8080
# exec로 java를 PID 1로 → SIGTERM 전달되어 graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
