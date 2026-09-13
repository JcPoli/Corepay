# Multi-stage build: the runtime image carries a JRE and the jar, nothing else.
# A smaller image is not just faster to ship — it is a smaller surface for
# Trivy to find vulnerabilities in.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
# Dependencies resolve in their own layer so code changes do not re-download.
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN apk update && apk upgrade --no-cache
RUN addgroup -S corepay && adduser -S corepay -G corepay
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
# Never run a payments service as root.
USER corepay
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -q -O /dev/null http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
