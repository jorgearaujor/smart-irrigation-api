FROM ubuntu:latest AS build

RUN apt-get update
RUN apt-get install openjdk-12-jdk -y
COPY . .

RUN apt-get install maven -y
RUN mvn clean install

FROM openjdk:17-jdk-slim

EXPOSE 8080

COPY --from=build /target/smart-irrigation-1.0.0.jar app.jar

ENTRYPOINT [ "java", "-jar", "app.jar" ]