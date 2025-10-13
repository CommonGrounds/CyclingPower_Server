# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Explicitly copy DB file
COPY cycling_power.db /app/cycling_power.db
# Copy the entire json folder
COPY json /app/json
COPY images /app/images
RUN mkdir -p /app/Uploads && chmod -R 755 /app
RUN chmod -R 644 /app/json /app/images && chmod 664 /app/cycling_power.db
RUN ls -l /app/cycling_power.db || echo "DB file not found"
RUN ls -l /app/json || echo "json folder not found"
RUN apt-get update && apt-get install -y git
RUN git config --global user.email "java4now@gmail.com" && \
    git config --global user.name "CyclingPower_Server"
ARG GIT_TOKEN
RUN if [ -n "$GIT_TOKEN" ]; then \
        git clone https://x:${GIT_TOKEN}@github.com/CommonGrounds/CyclingPower_Server.git /app/repo && \
        mv /app/repo/.git /app/.git && \
        rm -rf /app/repo; \
    else \
        echo "GIT_TOKEN not provided, skipping clone"; \
    fi

ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_ADDRESS=0.0.0.0
ENV PORT=10000

# Expose the port
EXPOSE ${PORT}

ENTRYPOINT ["java", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# docker run --rm -it app
# sudo docker run -p 8080:8080 app
# odnosno realna ( prema ovoj konfiguraciji ) lokalna provera
# docker run -p 8080:10000 -e SPRING_PROFILES_ACTIVE=prod -e PORT=10000 app
# sa http://localhost:8080/ treba da vrati - Server is running - Mon Oct 13 10:21:53 UTC 2025
