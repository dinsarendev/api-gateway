# Stage 1: Build the application
FROM gradle:8.5-jdk21 AS builder

# Set the working directory
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./

# Copy source code
COPY src ./src

# Set timezone explicitly
ENV TZ=Asia/Phnom_Penh

# Configure the timezone in the container
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

RUN gradle wrapper

# Make the wrapper executable and build the application
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jdk-alpine

# Set timezone via JVM (no /etc/timezone or shell available)
ENV TZ=Asia/Phnom_Penh

# Use a known non-root UID/GID (e.g. 1001) — ensure it exists in builder stage
# Use absolute path for JAR, as distroless has minimal filesystem
COPY --chown=1001:1001 --from=builder /app/build/libs/*.jar /app/app.jar
# Expose application port (adjust if necessary)
EXPOSE 25010
# Switch to non-root user
USER 1001

# Run Spring Boot app
ENTRYPOINT ["java", "-Duser.timezone=Asia/Phnom_Penh", "-jar", "/app/app.jar"]
