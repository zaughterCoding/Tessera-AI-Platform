ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:8-jre@sha256:165366caa3881d47586ff7fb63787e4e682c79b6d1fdff31f4be8f45df3fd45a
FROM ${JAVA_RUNTIME_IMAGE}
WORKDIR /app
COPY target/ai-creation-platform.jar /app/app.jar
RUN mkdir -p /app/media && chown -R 10001:10001 /app/media
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Duser.timezone=UTC", "-jar", "/app/app.jar"]
