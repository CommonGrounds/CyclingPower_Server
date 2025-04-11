#
# Build stage
#
FROM maven:3.8.2-jdk-11 AS build
COPY . .
RUN mvn clean package -DskipTests


# Copy the JAR from the build stage
FROM openjdk:19-jdk AS build
COPY --from=build target/CyclingPower_Server_SQL_Clone-1.0-SNAPSHOT.jar CyclingPower_Server_SQL_Clone-1.0-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","CyclingPower_Server_SQL_Clone-1.0-SNAPSHOT.jar"]
EXPOSE 8080