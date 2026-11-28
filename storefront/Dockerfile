# syntax=docker/dockerfile:1

FROM node:26.13.1-slim AS builder

ARG PNPM_VERSION=12.21.0
ARG VITE_APP_VERSION=dev

ENV VITE_APP_VERSION=${VITE_APP_VERSION}

WORKDIR /workspace/storefront

RUN npm install -g "pnpm@${PNPM_VERSION}"

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile

COPY . .
RUN pnpm run build

FROM nginx/nginx-unprivileged:1.31.10-alpine-slim AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /workspace/storefront/dist /usr/share/nginx/html

EXPOSE 8080
