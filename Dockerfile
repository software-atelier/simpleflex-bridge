FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package

FROM eclipse-temurin:17-jre
RUN useradd --system --uid 10001 --create-home bridge
WORKDIR /app
COPY --from=build /build/target/simpleflex-bridge-0.1.0.jar app.jar
COPY --from=build /build/target/lib ./lib
USER bridge
EXPOSE 8080
ENTRYPOINT ["java", "-Dlog4j2.configurationFile=classpath:log4j2.xml", "-jar", "/app/app.jar"]
