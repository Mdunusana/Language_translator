// Captures the README screenshots with a headless Edge or Chrome via the DevTools protocol.
// No npm packages: uses Node 22+'s built-in fetch and WebSocket.
//
// 1. Start the app (backend + `node frontend/dev-server.mjs`) with real AI provider keys.
// 2. Sign in once and translate a few phrases, so the dashboard and history have real content.
// 3. Run:
//      DEMO_EMAIL=you@example.com DEMO_PASSWORD=... node scripts/capture-screenshots.mjs
//    Options: APP_URL (default http://localhost:5500), BROWSER (path to msedge/chrome), OUT_DIR (default docs/screenshots)
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const APP = process.env.APP_URL ?? 'http://localhost:5500';
const OUT = process.env.OUT_DIR ?? 'docs/screenshots';
const { DEMO_EMAIL, DEMO_PASSWORD } = process.env;
if (!DEMO_EMAIL || !DEMO_PASSWORD) {
  console.error('Set DEMO_EMAIL and DEMO_PASSWORD (an account to sign in with).');
  process.exit(1);
}
const BROWSER = process.env.BROWSER ?? [
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].find((path) => existsSync(path));
if (!BROWSER) {
  console.error('No Edge/Chrome found. Set BROWSER to its executable path.');
  process.exit(1);
}

const PORT = 9333;
const wait = (ms) => new Promise((r) => setTimeout(r, ms));
mkdirSync(OUT, { recursive: true });

const browser = spawn(BROWSER, ['--headless=new', `--remote-debugging-port=${PORT}`,
  `--user-data-dir=${mkdtempSync(join(tmpdir(), 'lv-shots-'))}`, '--hide-scrollbars', '--no-first-run', '--mute-audio', 'about:blank'],
{ stdio: 'ignore' });

let target;
for (let i = 0; i < 50 && !target; i++) {
  await wait(200);
  try { target = (await (await fetch(`http://127.0.0.1:${PORT}/json/list`)).json()).find((t) => t.type === 'page'); } catch { /* starting */ }
}
if (!target) throw new Error('The browser did not start.');

const ws = new WebSocket(target.webSocketDebuggerUrl);
await new Promise((resolve) => ws.addEventListener('open', resolve, { once: true }));
let nextId = 0;
const pending = new Map();
let onEvent = () => {};
ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data);
  if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id); }
  if (msg.method) onEvent(msg);
});
const send = (method, params = {}) => new Promise((resolve, reject) => {
  const id = ++nextId;
  pending.set(id, (m) => (m.error ? reject(new Error(`${method}: ${m.error.message}`)) : resolve(m.result)));
  ws.send(JSON.stringify({ id, method, params }));
});
async function open(path, settleMs = 2500) {
  const loaded = new Promise((resolve) => { onEvent = (m) => { if (m.method === 'Page.loadEventFired') resolve(); }; });
  await send('Page.navigate', { url: `${APP}/${path}` });
  await loaded;
  await wait(settleMs);
}
const evaluate = (expression) => send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
const size = (width, height, mobile = false) =>
  send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: mobile ? 2 : 1.5, mobile });
async function shot(name) {
  const { data } = await send('Page.captureScreenshot', { format: 'png' });
  writeFileSync(join(OUT, name), Buffer.from(data, 'base64'));
  console.log('saved', join(OUT, name));
}

await send('Page.enable');
await size(1280, 800);
await open('index.html', 1800);
await shot('landing.png');
await open('register.html', 1500);
await shot('register.png');

const auth = await (await fetch(`${APP}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: DEMO_EMAIL, password: DEMO_PASSWORD }),
})).json();
if (!auth.accessToken) throw new Error(`Sign-in failed: ${auth.message ?? 'unknown error'}`);
await evaluate(`sessionStorage.setItem('linguavoice.token', ${JSON.stringify(JSON.stringify({ token: auth.accessToken, expiresAt: auth.expiresAt }))});
  sessionStorage.setItem('linguavoice.user', ${JSON.stringify(JSON.stringify(auth.user))}); true`);

for (const page of ['translator', 'dashboard', 'history', 'profile']) {
  await open(`${page}.html`);
  await shot(`${page}.png`);
}
await size(390, 844, true);
await open('translator.html');
await shot('translator-mobile.png');

ws.close();
browser.kill();
process.exit(0);
