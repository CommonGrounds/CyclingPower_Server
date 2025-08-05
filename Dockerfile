# Build stage
   FROM maven:3.9.6-eclipse-temurin-17 AS build
   WORKDIR /app
   COPY . .
   RUN mvn clean package -DskipTests

   # Runtime stage
   FROM openjdk:17-jdk-slim
   WORKDIR /app
   COPY --from=build /app/target/*.jar app.jar
   RUN apt-get update && apt-get install -y curl libcrypto++-dev python3 python3-pip && rm -rf /var/lib/apt/lists/*
   RUN pip3 install gdown
   RUN gdown https://drive.google.com/uc?id=1yHcoc805FJRd-WLlDVaYguL5I8YesWy5 -O /usr/lib/libmega.so
   COPY cycling_power.db /app/cycling_power.db
   COPY json /app/json
   COPY images /app/images
   RUN mkdir -p /app/Uploads && chmod -R 755 /app
   RUN chmod -R 644 /app/json /app/images && chmod 664 /app/cycling_power.db
   EXPOSE 8080
   ENV SPRING_PROFILES_ACTIVE=prod
   ENV JAVA_LIBRARY_PATH=/usr/lib
   ENTRYPOINT ["java", "-Djava.library.path=${JAVA_LIBRARY_PATH}", "-Dserver.port=${PORT:8080}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app