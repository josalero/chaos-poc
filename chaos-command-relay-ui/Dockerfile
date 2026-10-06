# Operator console — build context is the chaos-poc repo root
FROM node:22-alpine AS build
WORKDIR /app

COPY chaos-command-relay-ui/package.json chaos-command-relay-ui/package-lock.json* ./chaos-command-relay-ui/

WORKDIR /app/chaos-command-relay-ui
RUN npm install

COPY chaos-command-relay-ui/ ./

ARG VITE_RELAY_API_URL=/api/relay
ARG VITE_VERIFY_UI_URL=/
ENV VITE_RELAY_API_URL=$VITE_RELAY_API_URL
ENV VITE_VERIFY_UI_URL=$VITE_VERIFY_UI_URL

RUN npm run build

FROM nginx:1.27-alpine
COPY chaos-command-relay-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/chaos-command-relay-ui/dist /usr/share/nginx/html
EXPOSE 80
