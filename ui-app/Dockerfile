# UI container
FROM eclipse-temurin:21-jre

WORKDIR /app

ARG JAR_FILE=target/ui-app-1.0-SNAPSHOT.jar
COPY ${JAR_FILE} app.jar

EXPOSE 8081
ENTRYPOINT ["java","-jar","/app/app.jar"]
