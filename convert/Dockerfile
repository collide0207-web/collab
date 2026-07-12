# syntax=docker/dockerfile:1
# Collide document conversion service (PPTX/PPT -> PDF via LibreOffice headless).

# --- build stage: compile TypeScript to dist/ ---
FROM node:20-slim AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY tsconfig.json ./
COPY src ./src
RUN npm run build

# --- run stage: node + LibreOffice + core fonts ---
FROM node:20-slim AS run
WORKDIR /app
ENV NODE_ENV=production

# libreoffice-impress is enough to convert presentations; the font packages give
# reasonable fidelity for common decks (Liberation ~= Arial/Times, Noto = broad
# Unicode/CJK coverage). --no-install-recommends keeps the image lean.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libreoffice-impress \
        fonts-liberation \
        fonts-noto-core \
        fonts-noto-cjk \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system app && useradd --system --gid app --home /app app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist

RUN chown -R app:app /app
USER app

ENV PORT=4500
EXPOSE 4500

# Node-based healthcheck (no curl in the image).
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
    CMD node -e "fetch('http://localhost:'+(process.env.PORT||4500)+'/health').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"

ENTRYPOINT ["node", "dist/server.js"]
