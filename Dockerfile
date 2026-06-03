FROM nexus.cambofreelance.com/docker-hosted/core/gradle:8.5-jdk21 AS builder

WORKDIR /app

COPY gradle gradle
COPY build.gradle settings.gradle ./
# ✅ Copy credentials file
COPY gradle.properties /root/.gradle/gradle.properties
COPY src ./src

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
