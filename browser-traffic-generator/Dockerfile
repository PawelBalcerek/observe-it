FROM node:26-slim AS build

ARG PNPM_VERSION=12.23.0

WORKDIR /app

RUN npm install -g "pnpm@${PNPM_VERSION}"

COPY package.json pnpm-lock.yaml tsconfig.json tsconfig.build.json ./
RUN pnpm install --frozen-lockfile

COPY src ./src
RUN pnpm run build && pnpm prune --prod

FROM node:26-slim AS runtime

ENV NODE_ENV=production
ENV PORT=8082
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

WORKDIR /app

COPY --from=build /app/package.json ./package.json
COPY --from=build /app/node_modules ./node_modules

RUN ./node_modules/.bin/playwright-core install --only-shell --with-deps chromium \
    && chmod -R a+rX "${PLAYWRIGHT_BROWSERS_PATH}" \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/dist ./dist

USER node

EXPOSE 8082

CMD ["node", "dist/main.js"]
