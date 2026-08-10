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

# Copy the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
