FROM eclipse-temurin:17-jdk

WORKDIR /app
COPY . .

# Compile project classes into /app/classes
RUN chmod +x compile.sh && ./compile.sh

EXPOSE 8080

# WebServer reads PORT env var (defaults to 8080)
CMD ["sh", "-c", "java -cp \"classes:lib/*\" splendor.web.WebServer"]

