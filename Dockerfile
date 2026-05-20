FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S quizarena \
    && adduser -S quizarena -G quizarena \
    && mkdir -p /app/uploads \
    && chown -R quizarena:quizarena /app

WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar

ENV JAVA_OPTS=""
EXPOSE 8081

USER quizarena
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dfile.encoding=UTF-8 -jar /app/app.jar"]
