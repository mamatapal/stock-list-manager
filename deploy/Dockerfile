# Runtime image for the Stock List Manager backend. Build the jar first (scripts/package-backend.ps1),
# which places it next to this file as app.jar.
FROM eclipse-temurin:21-jre-jammy

RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY app.jar /app/app.jar
USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    TZ=UTC
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
