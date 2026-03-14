FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S platform && adduser -S platform -G platform
USER platform

WORKDIR /app

COPY platform-integration/target/platform-integration-*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
