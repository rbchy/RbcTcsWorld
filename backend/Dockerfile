# ---------- build stage: Maven + JDK, never shipped ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# dependencies first: this layer is cached until pom.xml changes, so code changes rebuild in seconds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests package && cp target/rbctcsworld-shop-backend-*.jar /app.jar

# ---------- runtime stage: JRE only ----------
FROM eclipse-temurin:21-jre
# curl only for the container health check
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 --no-create-home app
WORKDIR /app
COPY --from=build /app.jar app.jar
# never run as root inside the container
USER app
EXPOSE 8081
HEALTHCHECK --interval=5s --timeout=3s --start-period=40s --retries=20 \
  CMD curl -fsS http://localhost:8081/actuator/health || exit 1
# MaxRAMPercentage: the JVM sizes its heap from the container memory limit, not from the host
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
