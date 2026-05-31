'use strict';

const http  = require('http');
const https = require('https');

const PORT = process.env.PORT || 3001;

// Origins allowed to call this proxy
const ALLOWED_ORIGINS = [
  'http://localhost:5173',
  'http://localhost:3000',
  'http://127.0.0.1:5173',
  'https://gangster2490.github.io',
  // add your custom domain here if needed
];

function corsHeaders(origin) {
  const allowed = ALLOWED_ORIGINS.includes(origin) ? origin : ALLOWED_ORIGINS[0];
  return {
    'Access-Control-Allow-Origin':  allowed,
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, x-api-key-fwd',
    'Access-Control-Max-Age':       '86400',
  };
}

const server = http.createServer((req, res) => {
  const origin = req.headers['origin'] || '';

  // ── CORS preflight ──
  if (req.method === 'OPTIONS') {
    res.writeHead(204, corsHeaders(origin));
    res.end();
    return;
  }

  // ── Health check ──
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json', ...corsHeaders(origin) });
    res.end(JSON.stringify({ status: 'ok' }));
    return;
  }

  // ── Main proxy endpoint ──
  if (req.method === 'POST' && req.url === '/api/generate') {
    const apiKey = req.headers['x-api-key-fwd'];
    if (!apiKey || !apiKey.startsWith('sk-ant-')) {
      res.writeHead(401, { 'Content-Type': 'application/json', ...corsHeaders(origin) });
      res.end(JSON.stringify({ error: 'Missing or invalid API key. Send it in x-api-key-fwd header.' }));
      return;
    }

    let body = '';
    req.on('data', chunk => { body += chunk; });
    req.on('end', () => {
      // Validate JSON before forwarding
      try { JSON.parse(body); } catch {
        res.writeHead(400, { 'Content-Type': 'application/json', ...corsHeaders(origin) });
        res.end(JSON.stringify({ error: 'Invalid JSON body.' }));
        return;
      }

      const options = {
        hostname: 'api.anthropic.com',
        port:     443,
        path:     '/v1/messages',
        method:   'POST',
        headers: {
          'Content-Type':      'application/json',
          'Content-Length':    Buffer.byteLength(body),
          'x-api-key':         apiKey,       // forwarded from browser, never stored
          'anthropic-version': '2023-06-01',
        },
      };

      const proxyReq = https.request(options, proxyRes => {
        let data = '';
        proxyRes.on('data', chunk => { data += chunk; });
        proxyRes.on('end', () => {
          res.writeHead(proxyRes.statusCode, {
            'Content-Type': 'application/json',
            ...corsHeaders(origin),
          });
          res.end(data);
        });
      });

      proxyReq.on('error', err => {
        console.error('[Proxy] Anthropic request error:', err.message);
        res.writeHead(502, { 'Content-Type': 'application/json', ...corsHeaders(origin) });
        res.end(JSON.stringify({ error: `Upstream error: ${err.message}` }));
      });

      proxyReq.write(body);
      proxyReq.end();
    });

    return;
  }

  // ── 404 ──
  res.writeHead(404, { 'Content-Type': 'application/json', ...corsHeaders(origin) });
  res.end(JSON.stringify({ error: 'Not found.' }));
});

server.listen(PORT, () => {
  console.log(`✅ TikTok Shop Creator Proxy running on http://localhost:${PORT}`);
  console.log(`   API key: provided per-request by the browser (never stored)`);
  console.log(`   Allowed origins: ${ALLOWED_ORIGINS.join(', ')}`);
});
