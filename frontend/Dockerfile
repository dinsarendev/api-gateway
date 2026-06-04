# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM node:20-alpine AS builder

WORKDIR /app

COPY package*.json ./
RUN npm ci

COPY . .

# BUILD_MODE maps to a package.json script: build:dev | build:staging | build:prod
ARG BUILD_MODE=staging
RUN npm run build:${BUILD_MODE}

# ── Stage 2: Serve ────────────────────────────────────────────────────────────
FROM nginx:alpine

# Runtime env var — set when running the container
# e.g. docker run -e BACKEND_URL=https://gateway-api.prod.cambofreelance.com ...
ENV BACKEND_URL=http://localhost:25010

COPY --from=builder /app/dist               /usr/share/nginx/html
COPY nginx.conf.template                    /etc/nginx/nginx.conf.template
COPY docker-entrypoint.sh                   /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]
