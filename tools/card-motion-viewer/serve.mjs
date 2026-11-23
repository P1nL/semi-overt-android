import http from 'node:http';
import {createReadStream} from 'node:fs';
import {stat} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const assetRoot = path.resolve(process.argv[2] || path.join(root, 'assets'));
const port = Number(process.argv[3] || 5193);
const mime = {'.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8', '.webp': 'image/webp'};
http.createServer(async (request, response) => {
  try {
    const pathname = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    const assets = pathname.startsWith('/assets/');
    const base = assets ? assetRoot : root;
    const relative = assets ? pathname.slice('/assets/'.length) : pathname === '/' ? 'index.html' : pathname.slice(1);
    const file = path.resolve(base, relative);
    const within = path.relative(base, file);
    if (within.startsWith('..') || path.isAbsolute(within)) {
      response.writeHead(403); response.end(); return;
    }
    const info = await stat(file);
    if (!info.isFile()) {response.writeHead(404); response.end(); return;}
    response.writeHead(200, {'Content-Type': mime[path.extname(file)] || 'application/octet-stream',
      'Content-Length': info.size, 'Cache-Control': 'no-cache'});
    createReadStream(file).pipe(response);
  } catch {
    response.writeHead(404); response.end('Not found');
  }
}).listen(port, '127.0.0.1', () => console.log(`Card motion viewer: http://127.0.0.1:${port}`));
