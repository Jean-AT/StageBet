FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN apk upgrade --no-cache libexpat
WORKDIR /app
COPY --from=build /workspace/target/StageBet-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
USER 10001
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
