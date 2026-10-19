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

# Detect which JDK modules the app needs
RUN jar xf target/*.jar && \
    jdeps --ignore-missing-deps --print-module-deps \
      --multi-release 21 \
      -cp 'BOOT-INF/lib/*' target/*.jar > /tmp/modules.txt

# Stage 2: Build custom JRE from Alpine JDK (musl-compatible for Alpine runtime)
FROM eclipse-temurin:21-jdk-alpine AS jre-builder
COPY --from=builder /tmp/modules.txt /tmp/modules.txt
RUN jlink --add-modules $(cat /tmp/modules.txt) \
      --strip-debug --no-man-pages --no-header-files \
      --compress=zip-6 --output /custom-jre

# Stage 3: Minimal runtime image
FROM alpine:3.21
WORKDIR /app

# Install curl for container health checks
RUN apk add --no-cache curl

# Copy custom JRE and application
COPY --from=jre-builder /custom-jre /opt/java
COPY --from=builder /workspace/target/*.jar app.jar
ENV PATH="/opt/java/bin:$PATH"

# Create non-root user for running the application
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

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
