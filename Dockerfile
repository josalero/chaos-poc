# Multi-module build; compose selects the runtime image via build.target
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY chaos-lib/pom.xml chaos-lib/
COPY chaos-command-relay/pom.xml chaos-command-relay/
COPY chaos-poc-downstream/pom.xml chaos-poc-downstream/
COPY chaos-poc-demo/pom.xml chaos-poc-demo/
COPY discovery-server/pom.xml discovery-server/
COPY config-server/pom.xml config-server/
COPY gateway/pom.xml gateway/
COPY auth-server/pom.xml auth-server/

RUN mvn -B dependency:go-offline -pl discovery-server,config-server,gateway,auth-server,chaos-command-relay,chaos-poc-demo,chaos-poc-downstream -am

COPY chaos-lib/src chaos-lib/src
COPY chaos-command-relay/src chaos-command-relay/src
COPY chaos-poc-downstream/src chaos-poc-downstream/src
COPY chaos-poc-demo/src chaos-poc-demo/src
COPY discovery-server/src discovery-server/src
COPY config-server/src config-server/src
COPY gateway/src gateway/src
COPY auth-server/src auth-server/src
COPY config-repo config-repo

RUN mvn -B package -pl discovery-server,config-server,gateway,auth-server,chaos-command-relay,chaos-poc-demo,chaos-poc-downstream -am -DskipTests

FROM eclipse-temurin:21-jre-jammy AS runtime-base
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app

FROM runtime-base AS discovery-server
COPY --from=build /app/discovery-server/target/discovery-server-*.jar /app/app.jar
EXPOSE 8761
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=40s \
  CMD curl -fsS http://localhost:8761/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS config-server
COPY --from=build /app/config-server/target/config-server-*.jar /app/app.jar
COPY --from=build /app/config-repo /config-repo
ENV CONFIG_REPO_LOCATION=file:/config-repo
EXPOSE 8888
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=40s \
  CMD curl -fsS http://localhost:8888/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS gateway
COPY --from=build /app/gateway/target/gateway-*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=40s \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS auth-server
COPY --from=build /app/auth-server/target/auth-server-*.jar /app/app.jar
EXPOSE 9000
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=30s \
  CMD curl -fsS http://localhost:9000/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS chaos-command-relay
COPY --from=build /app/chaos-command-relay/target/chaos-command-relay-*.jar /app/app.jar
EXPOSE 8090
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=40s \
  CMD curl -fsS http://localhost:8090/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS chaos-poc-downstream
COPY --from=build /app/chaos-poc-downstream/target/chaos-poc-downstream-*.jar /app/app.jar
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=30s \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM runtime-base AS chaos-poc-demo
COPY --from=build /app/chaos-poc-demo/target/chaos-poc-demo-*.jar /app/app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=12 --start-period=60s \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
