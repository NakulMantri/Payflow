# Multi-stage Dockerfile for PayFlow Backend
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build application
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Create non-root system user
RUN groupadd -r payflow && useradd -r -g payflow payflow

COPY --from=build /app/target/payflow-backend-1.0.0.jar app.jar
RUN chown -R payflow:payflow /app

USER payflow

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+EnableDynamicAgentLoading", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
