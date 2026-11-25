# LinguaVoice backend: build with Maven, run on a small Java 21 runtime as a non-root user.
# Tests are not run here (they run in `mvn test` / CI); the image only packages the application.

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies first, so they are cached until pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests && cp target/linguavoice-backend-*.jar /build/app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /build/app.jar /app/app.jar
USER app

EXPOSE 8081
# Configuration comes from environment variables (see .env.example); no secrets are baked into the image.
ENV SERVER_PORT=8081 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:8081/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
