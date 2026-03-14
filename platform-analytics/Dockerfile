FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S platform && adduser -S platform -G platform
USER platform

WORKDIR /app

COPY platform-analytics/target/platform-analytics-*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
