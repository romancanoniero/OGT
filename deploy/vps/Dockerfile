FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY backend-dist/ /app/
ENV PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=10 --start-period=20s \
    CMD curl -fsS http://127.0.0.1:8080/health || exit 1
CMD ["/app/bin/backend"]
