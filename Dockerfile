# ============================================================
# Stage 1: Build — Maven + JDK 17 to produce the production JAR
# ============================================================
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /build

# Copy dependency manifests first (improves layer caching)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy full source and build the Vaadin production JAR
COPY src ./src
COPY frontend ./frontend
RUN mvn package -Pproduction -DskipTests -q

# ============================================================
# Stage 2: Runtime — slim JRE 17 image
# ============================================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the fat JAR from build stage
COPY --from=builder /build/target/trading-bot-1.0-SNAPSHOT.jar app.jar

# Cloud Run injects PORT env var; Spring Boot uses server.port
ENV SERVER_PORT=8080
EXPOSE 8080

# Memory-friendly JVM flags suitable for Cloud Run 512Mi containers
ENTRYPOINT ["java", \
  "-Xmx400m", \
  "-Xms128m", \
  "-XX:+UseContainerSupport", \
  "-Dspring.profiles.active=production", \
  "-jar", "app.jar"]
