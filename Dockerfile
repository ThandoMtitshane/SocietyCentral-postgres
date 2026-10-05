# ── Build stage ───────────────────────────────────────────────────────────────
# Uses a Maven image with JDK 25 to produce the executable fat jar.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app

# Cache dependencies first (only re-downloads when pom.xml changes).
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Build the application (skip tests for a faster, dependency-free deploy build).
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ── Run stage ───────────────────────────────────────────────────────────────--
# Smaller JRE-only image to run the packaged jar.
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the built jar (artifactId-version.jar) from the build stage.
COPY --from=build /app/target/SocietyCentral-0.0.1-SNAPSHOT.jar app.jar

# Render provides the port via $PORT; Spring Boot reads it via server.port.
EXPOSE 8080

# Cap the heap so the app fits Render's 512MB free instance.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"

ENTRYPOINT ["java", "-jar", "app.jar"]
