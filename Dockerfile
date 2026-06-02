FROM nexus.cambofreelance.com/docker-hosted/core/node:20-alpine AS frontend-builder

WORKDIR /app/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM nexus.cambofreelance.com/docker-hosted/core/gradle:8.5-jdk21 AS builder

WORKDIR /app

COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY gradle.properties /root/.gradle/gradle.properties
COPY src ./src

COPY --from=frontend-builder /app/src/main/resources/static ./src/main/resources/static

ENV TZ=Asia/Phnom_Penh

RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

RUN gradle bootJar --no-daemon

FROM nexus.cambofreelance.com/docker-hosted/core/eclipse-temurin:21-jdk-alpine

ENV TZ=Asia/Phnom_Penh

COPY --chown=1001:1001 --from=builder /app/build/libs/*.jar /app/app.jar

EXPOSE 25010

USER 1001

ENTRYPOINT ["java", "-Duser.timezone=Asia/Phnom_Penh", "-jar", "/app/app.jar"]