# DocShelf backend image: Maven build stage + JRE runtime with Tesseract (eng + hin). Build context = repo root (needs db/).
# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY db/migration ./db/migration
COPY backend/pom.xml ./backend/pom.xml
COPY backend/.mvn ./backend/.mvn
COPY backend/mvnw ./backend/mvnw
WORKDIR /workspace/backend
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN mvn -q -B -DskipTests package && cp target/docshelf-backend-*.jar /workspace/app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl tesseract-ocr tesseract-ocr-eng tesseract-ocr-hin \
 && rm -rf /var/lib/apt/lists/*
ENV TESSDATA_PREFIX=/usr/share/tesseract-ocr/4.00/tessdata \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom" \
    DOCSHELF_BLOB_DIR=/data/blobs
RUN useradd --system --uid 1001 --create-home docshelf \
 && mkdir -p /data/blobs && chown -R docshelf:docshelf /data
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER docshelf
VOLUME ["/data/blobs"]
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
