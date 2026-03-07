# Gradle Command Reference

## Build

```bash
# Build the project
./gradlew build

# Build without running tests
./gradlew build -x test

# Clean build output
./gradlew clean

# Clean and build
./gradlew clean build
```

## Run

```bash
# Run the Spring Boot application
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run with JVM arguments
./gradlew bootRun -Dspring-boot.run.jvmArguments="-Xms512m -Xmx1024m"
```

## Test

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.cambofreelance.apigateway.ApiGatewayApplicationTests"

# Run tests with verbose output
./gradlew test --info

# Generate test report (output: build/reports/tests/test/index.html)
./gradlew test --rerun-tasks
```

## Dependencies

```bash
# List all dependencies
./gradlew dependencies

# List dependencies for a specific configuration
./gradlew dependencies --configuration compileClasspath

# Check for dependency updates
./gradlew dependencyInsight --dependency spring-boot

# Refresh dependencies (force re-download)
./gradlew build --refresh-dependencies
```

## JAR / Package

```bash
# Build executable JAR (output: build/libs/)
./gradlew bootJar

# Build plain JAR (without Spring Boot loader)
./gradlew jar

# Extract JAR layers (for Docker optimization)
java -Djarmode=layertools -jar build/libs/api-gateway-0.0.1-SNAPSHOT.jar extract
```

## Info / Debug

```bash
# Show project properties
./gradlew properties

# Show all available tasks
./gradlew tasks

# Show all tasks including subtasks
./gradlew tasks --all

# Build with stacktrace (for debugging errors)
./gradlew build --stacktrace

# Build with detailed info logging
./gradlew build --info

# Build with debug logging
./gradlew build --debug

# Check Gradle version
./gradlew --version
```

## Cache / Performance

```bash
# Build with build cache enabled
./gradlew build --build-cache

# Clear Gradle cache
./gradlew cleanBuildCache

# Run with parallel execution
./gradlew build --parallel

# Run in offline mode (no dependency download)
./gradlew build --offline

# Dry run (show tasks without executing)
./gradlew build --dry-run
```

## Gradle Wrapper

```bash
# Update Gradle wrapper version
./gradlew wrapper --gradle-version=8.12

# Verify wrapper distribution
./gradlew wrapper --verify
```

## Docker (with Spring Boot)

```bash
# Build OCI image using Spring Boot Buildpacks
./gradlew bootBuildImage

# Build image with custom name
./gradlew bootBuildImage --imageName=com.cambofreelance/api-gateway:latest
```

## Windows Note

On Windows, use `gradlew.bat` instead of `./gradlew`:

```powershell
.\gradlew.bat build
.\gradlew.bat bootRun
.\gradlew.bat test
```
