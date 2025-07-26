# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
COPY cycling_power.db /app/cycling_power.db
COPY json /app/json
COPY images /app/images
COPY .gitignore /app/.gitignore
# Create directories for Render Disk
RUN mkdir -p /data/json /data/images /data/uploads /data/db && \
    chmod -R 775 /data && \
    chmod -R 775 /app && \
    chmod 664 /app/cycling_power.db
# Symlink to /data (assuming Render Disk at /data)
RUN mv /app/json /data/json && ln -s /data/json /app/json || true
RUN mv /app/images /data/images && ln -s /data/images /app/images || true
RUN mv /app/uploads /data/uploads && ln -s /data/uploads /app/uploads || true
RUN mv /app/cycling_power.db /data/db/cycling_power.db && ln -s /data/db/cycling_power.db /app/cycling_power.db || true
# Verify setup
RUN ls -la /data/json || { echo "data/json folder not found"; exit 1; }
RUN ls -la /data/images || { echo "data/images folder not found; exit 1; }
RUN ls -la /data/uploads || { echo "data/uploads folder not found"; exit 1; }
RUN ls -la /data/db/cycling_power.db || { echo "DB file not found"; exit 1; }
RUN ls -la /app/.gitignore || { echo ".gitignore not found"; exit 1; }
RUN apt-get update && apt-get install -y git sqlite3
RUN git config --global user.email "java4now@gmail.com" && \
    git config --global user.name "CyclingPower_Server"
ARG GIT_TOKEN
RUN if [ -n "$GIT_TOKEN" ]; then \
        git clone https://x:${GIT_TOKEN}@github.com/CommonGrounds/CyclingPower_Server.git /app/repo && \
        mv /app/repo/.git /app/.git && \
        mv /app/repo/.gitignore /app/.gitignore || true && \
        mv /app/repo/.idea /app/.idea || true && \
        cp -r /app/repo/* /app/ && \
        rm -rf /app/repo || { echo "Git clone failed"; exit 1; }; \
    else \
        echo "GIT_TOKEN not provided"; exit 1; \
    fi
RUN git status || { echo "Git status failed"; exit 1; }
RUN cat /app/.gitignore || { echo ".gitignore content not readable"; exit 1; }
RUN sqlite3 /data/db/cycling_power.db "SELECT name FROM sqlite_master WHERE type='table';" || { echo "SQLite check failed"; exit 1; }
EXPOSE 8080
ENTRYPOINT ["java", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app