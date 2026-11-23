# Build context is the repo root (see docker-compose.yml)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY claims-service/pom.xml claims-service/
COPY notification-service/pom.xml notification-service/
RUN mvn -q -pl notification-service -am dependency:go-offline
COPY notification-service/src notification-service/src
RUN mvn -q -pl notification-service -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/notification-service/target/notification-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
