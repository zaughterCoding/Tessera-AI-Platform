ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:21.0.13_9-jre@sha256:0b99b3376fbb50dbf9c0af8535fdd5b8eb0d3fdfc8b1e4967f383b6919f6901a
FROM ${JAVA_RUNTIME_IMAGE}
WORKDIR /app
COPY target/ai-creation-platform.jar /app/app.jar
RUN mkdir -p /app/media && chown -R 10001:10001 /app/media
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
