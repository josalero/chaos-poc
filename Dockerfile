# Multi-module build; compose selects the runtime image via build.target
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
COPY chaos-listener-lib/pom.xml chaos-listener-lib/
COPY chaos-command-relay/pom.xml chaos-command-relay/
COPY chaos-poc-downstream/pom.xml chaos-poc-downstream/
COPY chaos-poc-demo/pom.xml chaos-poc-demo/

RUN mvn -B dependency:go-offline -pl chaos-command-relay,chaos-poc-demo,chaos-poc-downstream -am

COPY chaos-listener-lib/src chaos-listener-lib/src
COPY chaos-command-relay/src chaos-command-relay/src
COPY chaos-poc-downstream/src chaos-poc-downstream/src
COPY chaos-poc-demo/src chaos-poc-demo/src

RUN mvn -B package -pl chaos-command-relay,chaos-poc-demo,chaos-poc-downstream -am -DskipTests

FROM eclipse-temurin:17-jre-jammy AS runtime-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

FROM runtime-base AS chaos-command-relay
COPY --from=build /app/chaos-command-relay/target/chaos-command-relay-*.jar /app/app.jar
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=40s \
  CMD curl -fsS http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker"]

FROM runtime-base AS chaos-poc-downstream
COPY --from=build /app/chaos-poc-downstream/target/chaos-poc-downstream-*.jar /app/app.jar
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=30s \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker,test,chaos-monkey"]

FROM runtime-base AS chaos-poc-demo
COPY --from=build /app/chaos-poc-demo/target/chaos-poc-demo-*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=60s \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar", "--spring.profiles.active=docker,test,chaos-monkey"]
