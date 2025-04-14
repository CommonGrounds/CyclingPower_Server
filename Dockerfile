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
RUN ls -l /app/cycling_power.db || echo "DB file not found"
RUN ls -l /app/json || echo "json folder not found"
# Install Git
RUN apt-get update && apt-get install -y git
# Configure Git
RUN git config --global user.email "server@cyclingpower.app" && \
    git config --global user.name "CyclingPower Server"
# Clone repository
ARG GIT_TOKEN
RUN git clone https://x:${GIT_TOKEN}@github.com/Wild_Camper/cyclingpower.git /app/repo && \
    mv /app/repo/.git /app/.git && \
    rm -rf /app/repo
EXPOSE 8080
ENTRYPOINT ["java", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app