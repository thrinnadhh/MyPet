FROM eclipse-temurin:21-jre-alpine

ARG SERVICE_NAME
ENV JAVA_OPTS="-Xmx256m -Xss512k"
# Match SchedulerRuntimeRole.parse(): a service image executes scheduled workers
# unless a deployment explicitly overrides this to API, WORKER, or DISABLED.
ENV MYPET_SCHEDULING_ROLE="ALL"
WORKDIR /app

COPY backend/${SERVICE_NAME}/build/libs/${SERVICE_NAME}-*-SNAPSHOT.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
