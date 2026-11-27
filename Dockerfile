# Imagen de la API: se compila con Maven en una etapa y se ejecuta con un JRE ligero en otra.
# La web (React + Vite) se compila aparte y se sirve en /app/ desde el mismo servicio.
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -q dependency:go-offline
COPY src src
COPY --from=web /web/dist src/main/resources/static/app
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home resi && mkdir -p /app/evidencias && chown resi /app/evidencias
COPY --from=build /app/target/*.jar app.jar
USER resi
ENV TZ=America/Lima
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
