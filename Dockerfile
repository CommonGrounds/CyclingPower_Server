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
    libsqlite3-0 \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*

# Install gdown and download SQLite driver
RUN pip3 install gdown && \
    gdown https://drive.google.com/uc?id=1yHcoc805FJRd-WLlDVaYguL5I8YesWy5 -O /usr/lib/libmega.so

# Copy application files
COPY --from=build /app/target/*.jar app.jar
COPY cycling_power.db /app/
RUN mkdir -p /app/json /app/images /app/Uploads && \
    chmod -R 755 /app && \
    chmod 664 /app/cycling_power.db

# Environment variables
ENV JAVA_LIBRARY_PATH=/usr/lib
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:file:/app/cycling_power.db

# Expose and run
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.library.path=${JAVA_LIBRARY_PATH}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app