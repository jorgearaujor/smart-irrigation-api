FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN mvn dependency:go-offline -B

COPY src src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring spring
USER spring

EXPOSE 8080

COPY --from=build /app/target/smart-irrigation-1.0.0.jar app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]