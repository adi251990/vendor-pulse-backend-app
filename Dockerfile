## Multi-stage Dockerfile for building and running the Spring Boot app
FROM maven:3.9.4-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy only files needed for dependency resolution first for better caching
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src ./src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
