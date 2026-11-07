FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre

# Set by the deployment workflow: which commit this image was built from, and when.
ARG VCS_REF=unknown
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.title="alivia-web-services" \
      org.opencontainers.image.description="Alivia API (modular monolith)" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

WORKDIR /app

# The application never needs root: it listens on 8080 and writes only to /tmp.
RUN groupadd --system --gid 10001 alivia \
    && useradd --system --uid 10001 --gid alivia --no-create-home --shell /usr/sbin/nologin alivia

COPY --from=build --chown=alivia:alivia /app/target/*.jar app.jar

USER alivia
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
