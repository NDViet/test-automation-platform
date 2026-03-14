FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S platform && adduser -S platform -G platform
USER platform

WORKDIR /app

COPY platform-ingestion/target/platform-ingestion-*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
