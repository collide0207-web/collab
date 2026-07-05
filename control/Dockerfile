# syntax=docker/dockerfile:1
# Multi-stage build for the Collide control plane (Spring Boot).

# --- build stage: compile + package the executable boot jar ---
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copy the Gradle wrapper + build files first so dependency resolution is cached
# independently of source changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Now the sources, and build (tests run in CI, not in the image build).
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# --- run stage: slim JRE, non-root, health-checkable ---
FROM eclipse-temurin:17-jre AS run
WORKDIR /app

# curl for the container HEALTHCHECK; run as an unprivileged user.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home /app app

COPY --from=build /app/build/libs/*-SNAPSHOT.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# exec form via sh so $JAVA_OPTS expands; Spring handles SIGTERM for graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
