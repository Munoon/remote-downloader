FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY . ./
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine-3.21
WORKDIR /app
COPY --from=builder /app/target/remote-downloader-*.jar remote-downloader.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "remote-downloader.jar"]