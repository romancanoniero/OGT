// Local-only static server and reverse proxy. Never proxies to production.
import http from 'node:http';
import net from 'node:net';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, process.env.OGT_QA_WEB_ROOT || '../web/app');
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg' };
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost:19082');
  if (/^\/(api\/|media\/|health$)/.test(url.pathname)) {
    const upstream = http.request({ hostname: '127.0.0.1', port: 19083, path: req.url, method: req.method, headers: req.headers }, reply => {
      res.writeHead(reply.statusCode, reply.headers); reply.pipe(res);
    });
    upstream.on('error', () => { res.writeHead(502); res.end('QA backend unavailable'); });
    req.pipe(upstream); return;
  }
  try {
    const local = url.pathname === '/' ? 'index.html' : decodeURIComponent(url.pathname.slice(1));
    const filename = path.resolve(root, local);
    if (!filename.startsWith(root + path.sep)) { res.writeHead(403); res.end(); return; }
    let data;
    if (url.pathname === '/qa-firebase-app.js' || url.pathname === '/qa-firebase-auth.js') {
      const name = url.pathname.includes('-auth') ? 'firebase-auth-compat.js' : 'firebase-app-compat.js';
      data = await readFile(path.join(here, 'node_modules/firebase', name));
    } else data = await readFile(filename);
    if (local === 'index.html') {
      data = Buffer.from(data.toString()
        .replace('<head>', '<head><script>window.OGT_API_BASE="";window.OGT_AUTH_EMULATOR="http://127.0.0.1:19099";window.OGT_FIREBASE_CONFIG={apiKey:"demo-key",projectId:"demo-ogt-qa",authDomain:"localhost"};</script>')
        .replace('https://www.gstatic.com/firebasejs/10.13.2/firebase-app-compat.js', '/qa-firebase-app.js')
        .replace('https://www.gstatic.com/firebasejs/10.13.2/firebase-auth-compat.js', '/qa-firebase-auth.js'));
    }
    res.writeHead(200, { 'Content-Type': types[path.extname(filename)] || 'application/octet-stream', 'Cache-Control': 'no-store' });
    res.end(data);
  } catch { res.writeHead(404); res.end('Not found'); }
});
server.on('upgrade', (req, socket, head) => {
  const upstream = net.connect(19083, '127.0.0.1', () => {
    upstream.write(`${req.method} ${req.url} HTTP/${req.httpVersion}\r\n`);
    for (let i = 0; i < req.rawHeaders.length; i += 2) upstream.write(`${req.rawHeaders[i]}: ${req.rawHeaders[i + 1]}\r\n`);
    upstream.write('\r\n'); upstream.write(head); socket.pipe(upstream); upstream.pipe(socket);
  });
  upstream.on('error', () => socket.destroy()); socket.on('error', () => upstream.destroy());
  socket.on('close', () => upstream.destroy());
});
server.listen(19082, 'localhost', () => console.log('OGT QA web: http://localhost:19082'));
