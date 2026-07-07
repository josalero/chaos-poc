# UI image — build context is chaos-poc repo root
FROM node:22-alpine AS build
WORKDIR /app

COPY chaos-poc-ui/package.json chaos-poc-ui/package-lock.json* ./chaos-poc-ui/

WORKDIR /app/chaos-poc-ui
RUN npm install

COPY chaos-poc-ui/ ./

ARG VITE_DEMO_URL=/api/demo
ARG VITE_OPERATOR_CONSOLE_URL=/operator/
ENV VITE_DEMO_URL=$VITE_DEMO_URL
ENV VITE_OPERATOR_CONSOLE_URL=$VITE_OPERATOR_CONSOLE_URL

RUN npm run build

FROM nginx:1.27-alpine
COPY chaos-poc-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/chaos-poc-ui/dist /usr/share/nginx/html
EXPOSE 80
