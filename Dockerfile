## Multi-stage Dockerfile for building and running the Spring Boot app
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy pom.xml and source code
COPY pom.xml ./
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
