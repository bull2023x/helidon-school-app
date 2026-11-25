FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /helidon
COPY pom.xml .
RUN mvn -B -ntp package -Dmaven.test.skip=true
COPY src ./src
RUN mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /helidon
COPY --from=build /helidon/target/myproject.jar ./myproject.jar
COPY --from=build /helidon/target/libs ./libs
COPY data ./data
EXPOSE 8080
CMD ["java", "-jar", "myproject.jar"]
