# syntax=docker/dockerfile:1

# Build stage
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

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
