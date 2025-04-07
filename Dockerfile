# Use a base image with Java (e.g., Eclipse Temurin based on Ubuntu)
FROM eclipse-temurin:17-jre-jammy

# Set working directory
WORKDIR /app

# Install Tesseract OCR and English language pack
# Update package lists and install dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    tesseract-ocr \
    tesseract-ocr-eng \
    && \
    # Clean up package lists
    rm -rf /var/lib/apt/lists/*

# Set the TESSDATA_PREFIX environment variable for Tesseract
# This path might vary slightly depending on the base image/distro
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata

# Expose the port Ktor will listen on
EXPOSE 8080

# Copy Gradle wrapper files
COPY gradlew .
COPY gradle gradle

# Copy build script
COPY build.gradle.kts .
# COPY settings.gradle.kts . # Removed as it's not present/needed for this setup

# Make gradlew executable
RUN chmod +x ./gradlew

# Download dependencies first to leverage Docker cache
# Copy only necessary files for dependency resolution
COPY src/main/kotlin /app/src/main/kotlin
# RUN ./gradlew build --no-daemon --stacktrace -x test # Build dependencies, skip tests for faster layer caching if needed
# For simplicity now, we'll build everything together later.

# Copy the rest of the application source code
COPY . .

# Build the application using Gradle wrapper
# Use --no-daemon to prevent Gradle daemon issues in Docker
RUN ./gradlew build --no-daemon --stacktrace

# Find the built JAR file (adjust pattern if needed)
# Assuming standard shadowJar or application plugin output location
# If using 'application' plugin, the distribution might be better.
# Let's assume a fat JAR for simplicity now.
# RUN ./gradlew shadowJar --no-daemon --stacktrace # If using shadow plugin
# ENTRYPOINT ["java", "-jar", "build/libs/mcp-ocr-1.0-SNAPSHOT-all.jar"] # If using shadow plugin

# If using the 'application' plugin's distribution:
# ENTRYPOINT ["build/install/mcp-ocr/bin/mcp-ocr"]

# Simpler approach: Run directly via Gradle (good for dev, less ideal for prod)
# CMD ["./gradlew", "run", "--no-daemon"]

# Better approach: Execute the JAR built by the application plugin
# Find the JAR in the build/libs directory
# The exact name depends on your build configuration
ENTRYPOINT ["java", "-jar", "build/libs/mcp-ocr-1.0-SNAPSHOT.jar"]