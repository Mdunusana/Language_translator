// Zero-dependency development server: serves the static frontend and proxies /api/* to the backend,
// so the browser only ever talks to one origin (exactly like Nginx in production, Phase 8).
//
// Usage:  node frontend/dev-server.mjs [--port 5500] [--api http://localhost:8081]
// The same options can be given as environment variables FRONTEND_PORT (or PORT) and API_ORIGIN.
//   port  where the frontend is served            (default 5500)
//   api   where the Spring Boot API is running    (default http://localhost:8081)
//
// ES modules cannot be loaded from file:// URLs, so the frontend must be served over HTTP.

import { createServer, request as httpRequest } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseArgs } from 'node:util';

const { values: args } = parseArgs({ options: { port: { type: 'string' }, api: { type: 'string' } } });

const ROOT = resolve(fileURLToPath(new URL('.', import.meta.url)));
const PORT = Number(args.port ?? process.env.FRONTEND_PORT ?? process.env.PORT ?? 5500);
const HOST = '127.0.0.1';
const API_ORIGIN = new URL(args.api ?? process.env.API_ORIGIN ?? 'http://localhost:8081');

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.ico': 'image/x-icon',
  '.webp': 'image/webp',
};

// Content-Security-Policy: scripts only from this origin (no inline scripts), network calls only to this
// origin. This limits what injected markup could do. Nginx sends the same policy in production.
const CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' https://fonts.googleapis.com",
  'font-src https://fonts.gstatic.com',
  "img-src 'self' data:",
  "media-src 'self' blob:",
  "connect-src 'self'",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
].join('; ');

const SECURITY_HEADERS = {
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'no-referrer',
  'Content-Security-Policy': CSP,
};

function send(res, status, body, type = 'text/plain; charset=utf-8') {
  res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-store', ...SECURITY_HEADERS });
  res.end(body);
}

/**
 * Streams the request to the backend and the response back (works for JSON, multipart and audio).
 * The Origin header is dropped: to the browser these are same-origin calls, so CORS does not apply,
 * and forwarding it would make Spring check it against the CORS allow-list. This is safe because
 * authentication uses bearer tokens, not cookies, so there is no cross-site request forgery risk.
 */
function proxyToApi(req, res) {
  const { origin, ...headers } = req.headers;
  const upstream = httpRequest({
    protocol: API_ORIGIN.protocol,
    hostname: API_ORIGIN.hostname,
    port: API_ORIGIN.port,
    method: req.method,
    path: req.url,
    headers: { ...headers, host: API_ORIGIN.host },
  }, (upstreamRes) => {
    res.writeHead(upstreamRes.statusCode ?? 502, upstreamRes.headers);
    upstreamRes.pipe(res);
  });
  upstream.on('error', () => {
    if (!res.headersSent) {
      send(res, 502, JSON.stringify({ status: 502, message: 'The LinguaVoice API is not running.' }),
        'application/json; charset=utf-8');
    } else {
      res.destroy();
    }
  });
  req.pipe(upstream);
}

async function serveStatic(req, res) {
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    return send(res, 405, 'Method not allowed');
  }

  let pathname;
  try {
    pathname = decodeURIComponent(new URL(req.url, `http://${HOST}`).pathname);
  } catch {
    return send(res, 400, 'Bad request');
  }
  if (pathname.endsWith('/')) pathname += 'index.html';

  const filePath = resolve(join(ROOT, normalize(pathname)));
  const insideRoot = filePath.startsWith(ROOT + sep);
  const hidden = pathname.split('/').some((part) => part.startsWith('.'));
  if (!insideRoot || hidden) {
    return send(res, 403, 'Forbidden');
  }

  try {
    const data = await readFile(filePath);
    send(res, 200, req.method === 'HEAD' ? undefined : data, MIME_TYPES[extname(filePath)] ?? 'application/octet-stream');
  } catch {
    send(res, 404, 'Not found');
  }
}

const server = createServer((req, res) => {
  if (req.url === '/api' || req.url.startsWith('/api/')) {
    proxyToApi(req, res);
  } else {
    serveStatic(req, res);
  }
});

server.listen(PORT, HOST, () => {
  console.log(`LinguaVoice frontend running at http://localhost:${PORT} (API proxied to ${API_ORIGIN.origin})`);
});
