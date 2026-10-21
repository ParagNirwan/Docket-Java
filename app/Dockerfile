# Stage 1: Build application
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /workspace

# Copy Maven wrapper and POM
COPY mvnw mvnw
COPY .mvn .mvn
COPY pom.xml pom.xml

# Normalize line endings and set permissions on mvnw
RUN tr -d '\r' < mvnw > mvnw_unix && mv mvnw_unix mvnw && chmod +x mvnw

# Pre-fetch dependencies for Docker cache layer
RUN ./mvnw dependency:go-offline -B || true

# Copy source code and build jar
COPY src src
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime image with full JRE
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install curl for container health checks
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Copy application
COPY --from=builder /workspace/target/*.jar app.jar

# Create non-root user for running the application
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

# Create uploads directory and set ownership
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
