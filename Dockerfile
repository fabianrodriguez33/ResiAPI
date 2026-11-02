# Imagen de la API: se compila con Maven en una etapa y se ejecuta con un JRE ligero en otra.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home resi && mkdir -p /app/evidencias && chown resi /app/evidencias
COPY --from=build /app/target/*.jar app.jar
USER resi
ENV TZ=America/Lima
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
