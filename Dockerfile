FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY . .

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

EXPOSE 8080

COPY --from=build /app/target/smart-irrigation-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]