# ============================================================
# Multi-stage Docker build for AI Competition Management System
# Stage 1: Build frontend (Node.js)
# Stage 2: Build backend (Maven + JDK 21) — uses image's built-in Maven
# Stage 3: Runtime (JDK 21 slim)
# ============================================================

# ---- Stage 1: Frontend Build ----
FROM node:22-alpine AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci || npm install
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: Backend Build ----
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app/backend
# Layer 1: POM only → cache Maven dependencies
COPY backend/pom.xml ./
RUN mvn dependency:go-offline -DskipTests -B
# Layer 2: Source + frontend dist
COPY backend/src ./src
RUN mkdir -p ./src/main/resources/static
COPY --from=frontend-builder /app/frontend/dist/ ./src/main/resources/static/
RUN mvn clean package -DskipTests -B

# ---- Stage 3: Runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app
COPY --from=backend-builder /app/backend/target/*.jar app.jar
ENV JAVA_OPTS="-Xmx256m -Xms128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
