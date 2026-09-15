# Build Stage
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy the Maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make the Maven wrapper executable
RUN chmod +x ./mvnw

# Download dependencies (this caches the dependencies layer)
RUN ./mvnw dependency:go-offline

# Copy the source code
COPY src ./src

# Build the application, skipping tests to speed up the build (tests should be run in CI)
RUN ./mvnw clean package -DskipTests

# Run Stage
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Needed for the HEALTHCHECK below
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Don't run the app as root
RUN groupadd -r linky && useradd -r -g linky linky
RUN mkdir -p /app/logs /app/uploads && chown -R linky:linky /app

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar
RUN chown linky:linky app.jar

USER linky

# Expose the port the app runs on
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
