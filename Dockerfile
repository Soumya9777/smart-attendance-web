# Build stage
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app
COPY src/ ./src/
RUN mkdir -p data
COPY nist.png ./

# Compile all Java files
RUN mkdir out && find src -name "*.java" > sources.txt && javac -d out @sources.txt

# Run stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy compiled classes
COPY --from=build /app/out ./out
RUN mkdir -p data
COPY --from=build /app/nist.png ./

# Expose port (Cloud providers will provide PORT env var)
EXPOSE 8080

# Start headless server
CMD ["java", "-cp", "out", "smartattendance.Main"]
