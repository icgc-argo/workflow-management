FROM openjdk:11-jdk
WORKDIR /usr/src/app
ADD . .
RUN ./mvnw package

FROM openjdk:11-jre-slim
COPY --from=0 /usr/src/app/target/workflow-management-*-SNAPSHOT.jar /usr/bin/workflow-management.jar
CMD ["java", "-ea", "-jar", "/usr/bin/workflow-management.jar"]
EXPOSE 8080/tcp