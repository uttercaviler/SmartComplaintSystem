FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY src/main/java/com/smartcomplaintsystem/SimpleBackendServer.java ./SimpleBackendServer.java
RUN mkdir -p /app/out /app/lib \
    && apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && curl -L -o /app/lib/mysql-connector-j-9.3.0.jar https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/9.3.0/mysql-connector-j-9.3.0.jar \
    && javac -cp /app/lib/mysql-connector-j-9.3.0.jar -d /app/out /app/SimpleBackendServer.java

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/out ./out
COPY --from=build /app/lib ./lib

EXPOSE 10000
CMD ["java", "-cp", "/app/out:/app/lib/mysql-connector-j-9.3.0.jar", "com.smartcomplaintsystem.SimpleBackendServer"]
