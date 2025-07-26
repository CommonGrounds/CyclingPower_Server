# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY cycling_power.db /app/cycling_power.db
COPY json /app/json
COPY images /app/images
RUN mkdir -p /app/Uploads && chmod -R 755 /app
RUN chmod -R 644 /app/json /app/images && chmod 664 /app/cycling_power.db
RUN ls -l /app/cycling_power.db || { echo "DB file not found"; exit 1; }
RUN ls -l /app/json || { echo "json folder not found"; exit 1; }
RUN ls -l /app/images || { echo "images folder not found"; exit 1; }
RUN apt-get update && apt-get install -y git
RUN git config --global user.email "java4now@gmail.com" && \
    git config --global user.name "CyclingPower_Server"
ARG GIT_TOKEN
RUN if [ -n "$GIT_TOKEN" ]; then \
        git clone --depth 1 https://x:${GIT_TOKEN}@github.com/CommonGrounds/CyclingPower_Server.git /app/repo && \
        mv /app/repo/.git /app/.git && \
        cp -r /app/repo/* /app/ && \
        rm -rf /app/repo || { echo "Git clone failed"; exit 1; }; \
    else \
        echo "GIT_TOKEN not provided, skipping clone"; \
    fi
EXPOSE 8080
ENTRYPOINT ["java", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app