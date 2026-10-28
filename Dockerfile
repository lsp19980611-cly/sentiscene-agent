FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/dist ./src/main/resources/static
RUN mvn -B verify

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /app/target/rain-cafe-1.0.0.jar app.jar
USER 10001
EXPOSE 8080
ENV BIND_ADDRESS=0.0.0.0
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","app.jar"]
