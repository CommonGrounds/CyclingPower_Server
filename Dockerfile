# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-noble
WORKDIR /app

# Install system dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    software-properties-common && \
    echo "deb http://archive.ubuntu.com/ubuntu plucky main" >> /etc/apt/sources.list && \
    apt-get update && \
    apt-cache search libicu76 && \
    apt-get install -y --no-install-recommends \
    wget \
    libatomic1 \
    libsqlite3-0 \
    libicu76 \
    && rm -rf /var/lib/apt/lists/* && \
    sed -i '/plucky/d' /etc/apt/sources.list

# Download libmega.so from GitHub Releases
RUN wget -O /usr/lib/libmega.so \
    "https://github.com/CommonGrounds/CyclingPower_Server/releases/download/v1.0-libmega/libmega.so" && \
    chmod +x /usr/lib/libmega.so

# Verify libmega.so dependencies
RUN ldd /usr/lib/libmega.so

# Copy application files
COPY --from=build /app/target/*.jar app.jar
COPY cycling_power.db /app/
RUN mkdir -p /app/json /app/images /app/Uploads && \
    chmod -R 755 /app && \
    chmod 664 /app/cycling_power.db

# Environment variables
ENV JAVA_LIBRARY_PATH=/usr/lib
ENV SPRING_DATASOURCE_URL=jdbc:sqlite:/app/cycling_power.db
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_ADDRESS=0.0.0.0
ENV PORT=10000

# Expose the port
EXPOSE ${PORT}

# Run the app with explicit binding
ENTRYPOINT ["java", "-Djava.library.path=${JAVA_LIBRARY_PATH}", "-Dserver.address=${SERVER_ADDRESS}", "-Dserver.port=${PORT}", "-jar", "app.jar"]

# Pokrenuti docker service -
# sudo dockerd
# Novi terminal, build pa run -
# sudo docker build -t app .
# sudo docker run -p 8080:8080 app
# ili
# sudo systemctl start docker ili sudo systemctl enable docker
# docker build -t cycling-app . && docker run --rm -it cycling-app ldd /usr/lib/libmega.so
# ili # Build the Docker image
# docker build -t cycling-app .
# Run the Docker container
# docker run -p 8080:10000 -e SPRING_PROFILES_ACTIVE=prod -e PORT=10000 -e MEGA_EMAIL=your_mega_email -e MEGA_PASSWORD=your_mega_password cycling-app
# Ctrl + D ili exit
# sudo systemctl stop docker.socket ili sudo systemctl disable docker.socket