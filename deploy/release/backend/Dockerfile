# 后端镜像（发布包）：app.jar 已经在自己电脑上用 mvn package 编译好，这里只放进 JRE 镜像
FROM eclipse-temurin:17-jre
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx448m -XX:+UseG1GC"
WORKDIR /app
COPY app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
