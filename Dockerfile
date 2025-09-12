# syntax=docker/dockerfile:1

# ---------- Build stage ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Maven Wrapper and project definition first to leverage layer caching
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .

# Pre-fetch dependencies (only re-run when pom.xml or wrapper changes)
RUN ./mvnw -q -e -DskipTests dependency:go-offline

# Now copy the actual source
COPY src ./src

# Package the application (tests skipped for faster image builds)
RUN ./mvnw -q -e -DskipTests package

# ---------- Runtime stage ----------
# Use a slim JRE base. Distroless is even smaller but lacks /bin/sh for HEALTHCHECK curl.
FROM eclipse-temurin:21-jre-jammy

# Create non-root user and group
RUN useradd --system --uid 10001 --create-home --shell /usr/sbin/nologin appuser

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Ensure files owned by non-root
RUN chown -R appuser:appuser /app
USER appuser

# JVM flags: exit on OOM and container-aware memory settings
ENV JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=50 -Dfile.encoding=UTF-8"

EXPOSE 8080

# Healthcheck against Spring Boot actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 CMD \
  curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java","-jar","/app/app.jar"]
