FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw clean package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/adminpro-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/uploads/documentos && chown -R app:app /app
USER app
EXPOSE 25565
ENTRYPOINT ["java", "-jar", "app.jar"]
