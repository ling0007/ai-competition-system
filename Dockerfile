# ============================================================
# Multi-stage Docker build for AI Competition Management System
# Stage 1: Build frontend (Node.js)
# Stage 2: Build backend (Maven + JDK 21)
# Stage 3: Runtime (JDK 21 slim)
# ============================================================

# ---- Stage 1: Frontend Build ----
FROM node:22-alpine AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --ignore-scripts 2>/dev/null || npm install --ignore-scripts
COPY frontend/ ./
RUN npm run build

# ---- Stage 2: Backend Build ----
FROM maven:3.9-eclipse-temurin-21-alpine AS backend-builder
WORKDIR /app/backend
# Copy Maven wrapper and POM first for dependency caching
COPY backend/pom.xml backend/.mvn ./
COPY backend/mvnw backend/mvnw.cmd ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -DskipTests -B 2>/dev/null || true
# Copy backend source (excluding static/ if it's in .gitignore)
COPY backend/src ./src
# Copy frontend build output into static resources directory (overwrites any pre-existing)
RUN mkdir -p ./src/main/resources/static
COPY --from=frontend-builder /app/frontend/dist/ ./src/main/resources/static/
RUN ./mvnw clean package -DskipTests -B

# ---- Stage 3: Runtime ----
FROM eclipse-temurin:21-jre-alpine
# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
WORKDIR /app
# Copy the built JAR
COPY --from=backend-builder /app/backend/target/*.jar app.jar
# JVM memory optimized for Render free tier (512MB total)
# -Xmx256m: max heap 256MB, leaving room for JVM overhead, native memory, and OS
ENV JAVA_OPTS="-Xmx256m -Xms128m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
