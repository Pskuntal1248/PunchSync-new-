
FROM openjdk:21-jdk-slim


WORKDIR /app


COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .


RUN ./mvnw dependency:go-offline


COPY src ./src


RUN ./mvnw package -DskipTests


EXPOSE 8080


CMD ["java", "-jar", "target/report-generator-0.0.1-SNAPSHOT.jar"]
