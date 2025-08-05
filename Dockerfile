# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    libsqlite3-0 \
    && rm -rf /var/lib/apt/lists/*

# Download libmega.so from GitHub Releases
RUN wget -O /usr/lib/libmega.so \
    "https://github.com/yourusername/yourrepo/releases/download/v1.0-libmega/libmega.so" && \
    chmod +x /usr/lib/libmega.so

# Verify the file exists
RUN ls -la /usr/lib/libmega.so

# Copy application files
COPY --from=build /app/target/*.jar app.jar
COPY cycling_power.db /app/
RUN mkdir -p /app/json /app/images /app/Uploads && \
    chmod -R 755 /app && \
    chmod 664 /app/cycling_power.db

# Environment variables
ENV JAVA_LIBRARY_PATH=/usr/lib
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:file:/app/cycling_power.db

# Explicitly set the server address and port
ENV SERVER_ADDRESS=0.0.0.0
ENV PORT=8080

# Expose the port
EXPOSE 8080

# Run the app with explicit binding
ENTRYPOINT ["java", "-Djava.library.path=/usr/lib", "-Dserver.address=${SERVER_ADDRESS}", "-Dserver.port=${PORT}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app