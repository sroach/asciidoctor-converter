FROM eclipse-temurin:21-jre-jammy

# Install GraphViz for diagram generation
RUN apt-get update && \
    apt-get install -y graphviz curl && \
    rm -rf /var/lib/apt/lists/*

# Create app user
RUN groupadd -r appuser && useradd -r -g appuser appuser

WORKDIR /app

# Create necessary directories
RUN mkdir -p /app/logs /app/staging /app/web /app/tmp && \
    chown -R appuser:appuser /app

# Copy JAR file (will be replaced by JReleaser)
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar

RUN chown appuser:appuser app.jar

USER appuser

EXPOSE 7202

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:7202/converter/actuator/health || exit 1

CMD ["java", "-jar", "app.jar"]
