FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:25-jre

# The base image can lag behind the distribution's security updates (Trivy gates the deployment on them): upgrade only
# the packages that have a fix, without installing anything new, and leave no package lists behind.
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade apt libapt-pkg7.0 \
    && rm -rf /var/lib/apt/lists/*

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
