FROM eclipse-temurin:21-jre-alpine

ARG SERVICE_NAME
ENV JAVA_OPTS="-Xmx256m -Xss512k"
WORKDIR /app

COPY backend/${SERVICE_NAME}/build/libs/${SERVICE_NAME}-*-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
