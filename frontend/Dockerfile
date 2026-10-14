# ---------- build stage ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY index.html vite.config.js ./
COPY public public
COPY src src
# VITE_DEMO=true (deploy/docker-compose.yml) adds the "demo shop" ribbon; empty for every other build
ARG VITE_DEMO=
ENV VITE_DEMO=$VITE_DEMO
RUN npm run build

# ---------- runtime stage: static files + reverse proxy ----------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=5s --timeout=3s --retries=10 CMD wget -qO- http://127.0.0.1/healthz || exit 1
