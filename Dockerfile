# syntax=docker/dockerfile:1
# 后端镜像：第一阶段用 Maven 编译，第二阶段只保留 JRE 和 jar，镜像更小
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY deploy/maven-settings.xml /root/.m2/settings.xml
COPY . .
# --mount=type=cache：依赖缓存在构建机上，第二次构建不用重新下载
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
WORKDIR /app
COPY --from=build /src/safework-server/target/safework-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
