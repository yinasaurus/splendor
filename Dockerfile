# Splendor Java HTTP server for Render (and similar) — uses PORT from the environment.
FROM eclipse-temurin:17-jdk-jammy

WORKDIR /app

# compile.sh needs bash (shopt, mktemp) and find
RUN apt-get update \
	&& apt-get install -y --no-install-recommends bash findutils \
	&& rm -rf /var/lib/apt/lists/*

COPY . .

RUN chmod +x compile.sh run_web.sh \
	&& bash compile.sh

EXPOSE 8080

# Render sets PORT at runtime; WebServer reads it
CMD ["bash", "run_web.sh"]
