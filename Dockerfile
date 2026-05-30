FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/adminpro-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 25565
ENTRYPOINT ["java", "-jar", "app.jar"]
