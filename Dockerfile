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
COPY credentials/cyclingpower-service-account.json /app/credentials/cyclingpower-service-account.json
RUN mkdir -p /app/Uploads && chmod -R 755 /app
RUN chmod -R 644 /app/json /app/images && chmod 664 /app/cycling_power.db
RUN apt-get update && apt-get install -y git
RUN git config --global user.email "java4now@gmail.com" && \
    git config --global user.name "CyclingPower_Server"
EXPOSE 8080
ENV GOOGLE_DRIVE_CREDENTIALS=/app/credentials/cyclingpower-service-account.json
ENTRYPOINT ["java", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app