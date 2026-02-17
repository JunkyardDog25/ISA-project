FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy Maven descriptor and resolve dependencies first (build cache optimization)
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline

# Copy source and build the application
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the fat Spring Boot jar from the build stage
COPY --from=build /app/target/jutjubic-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","app.jar"]

