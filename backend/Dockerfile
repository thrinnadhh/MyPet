FROM eclipse-temurin:21-jdk-alpine AS builder

ARG SERVICE_NAME
WORKDIR /workspace/backend

COPY backend/gradle ./gradle
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
COPY backend/common ./common
COPY backend/${SERVICE_NAME} ./${SERVICE_NAME}

RUN ./gradlew :${SERVICE_NAME}:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

ARG SERVICE_NAME
ENV JAVA_OPTS=""
WORKDIR /app

COPY --from=builder /workspace/backend/${SERVICE_NAME}/build/libs/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
