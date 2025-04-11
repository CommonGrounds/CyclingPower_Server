# Importing JDK and copying required files
FROM openjdk:19-jdk AS build
WORKDIR /app
COPY pom.xml .
COPY src src


# Copy the JAR from the build stage
FROM openjdk:19-jdk AS build
COPY --from=build target/CyclingPower_Server_SQL_Clone-1.0-SNAPSHOT.jar CyclingPower_Server_SQL_Clone-1.0-SNAPSHOT.jar
ENTRYPOINT ["java","-jar","CyclingPower_Server_SQL_Clone-1.0-SNAPSHOT.jar"]
EXPOSE 8080