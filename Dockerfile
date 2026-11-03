# Builds Tattoo Studio as one app (website + API) for hosting, e.g. on Render (see render.yaml).
# Settings come from environment variables (production profile): see README, "Production mode".

# 1. Build the website (client/dist)
FROM node:24-alpine AS client
WORKDIR /app/client
COPY client/package.json client/package-lock.json ./
RUN npm ci
COPY client/ ./
RUN npm run build

# 2. Build the server; Maven copies client/dist into the jar so it serves the website too.
#    Tests are not run here: they need a database, and GitHub Actions runs them on every push.
FROM maven:3.9-eclipse-temurin-21 AS server
WORKDIR /app/server
COPY server/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY server/src ./src
COPY --from=client /app/client/dist /app/client/dist
RUN mvn -B -q package -DskipTests

# 3. Run it with only a Java runtime, as an ordinary (non-root) user
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 tattoostudio
COPY --from=server /app/server/target/server-*.jar app.jar
USER tattoostudio
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080
# Hosts like Render say which port to use in PORT
CMD ["sh", "-c", "exec java -Dserver.port=${PORT:-8080} -jar app.jar"]
