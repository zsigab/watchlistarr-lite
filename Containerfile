## Stage 1: Build native image
## Uses the Quarkus GraalVM CE builder image; installs Maven since it's not bundled.
FROM quay.io/quarkus/ubi-quarkus-graalvmce-builder-image:jdk-21 AS build
USER root
COPY --from=maven:3.9 /usr/share/maven /usr/share/maven
RUN ln -sf /usr/share/maven/bin/mvn /usr/local/bin/mvn
USER quarkus

WORKDIR /code
COPY --chown=quarkus:quarkus pom.xml .
RUN mvn dependency:go-offline -q

COPY --chown=quarkus:quarkus src ./src
RUN mvn package -Pnative -DskipTests -q

## Stage 2: Minimal runtime image (no JVM)
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
RUN chown 1001 /work && chmod "g+rwX" /work && chown 1001:root /work
COPY --from=build --chown=1001:root /code/target/*-runner /work/application
VOLUME ["/config"]
USER 1001
ENTRYPOINT ["./application"]
