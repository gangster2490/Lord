'use strict';

const http  = require('http');
const https = require('https');

const PORT = process.env.PORT || 3001;

// Body size limit: 20 MB (for base64 images)
const MAX_BODY = 20 * 1024 * 1024;

function corsHeaders() {
  return {
    'Access-Control-Allow-Origin':  '*',
    'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, x-api-key-fwd',
    'Access-Control-Max-Age':       '86400',
  };
}

const server = http.createServer((req, res) => {

  // ── CORS preflight ──────────────────────────────────────────────────────────
  if (req.method === 'OPTIONS') {
    res.writeHead(204, corsHeaders());
    res.end();
    return;
  }

  // ── Health check ────────────────────────────────────────────────────────────
  if (req.method === 'GET' && req.url === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json', ...corsHeaders() });
    res.end(JSON.stringify({ status: 'ok', service: 'tiktok-shop-creator-proxy' }));
    return;
  }

  // ── Proxy endpoint ──────────────────────────────────────────────────────────
  if (req.method === 'POST' && req.url === '/api/generate') {

    const apiKey = req.headers['x-api-key-fwd'];
    if (!apiKey || !apiKey.startsWith('sk-ant-')) {
      res.writeHead(401, { 'Content-Type': 'application/json', ...corsHeaders() });
      res.end(JSON.stringify({ error: 'Missing or invalid Anthropic API key in x-api-key-fwd header.' }));
      return;
    }

    let body = '';
    let size = 0;

    req.on('data', chunk => {
      size += chunk.length;
      if (size > MAX_BODY) {
        req.destroy();
        res.writeHead(413, { 'Content-Type': 'application/json', ...corsHeaders() });
        res.end(JSON.stringify({ error: 'Request body too large (max 20 MB).' }));
        return;
      }
      body += chunk;
    });

    req.on('end', () => {
      try { JSON.parse(body); } catch {
        res.writeHead(400, { 'Content-Type': 'application/json', ...corsHeaders() });
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
          'x-api-key':         apiKey,
          'anthropic-version': '2023-06-01',
        },
      };

      const proxyReq = https.request(options, proxyRes => {
        let data = '';
        proxyRes.on('data', chunk => { data += chunk; });
        proxyRes.on('end', () => {
          console.log(`[proxy] ${new Date().toISOString()} status=${proxyRes.statusCode} bytes=${data.length}`);
          res.writeHead(proxyRes.statusCode, {
            'Content-Type': 'application/json',
            ...corsHeaders(),
          });
          res.end(data);
        });
      });

      proxyReq.on('error', err => {
        console.error('[proxy] upstream error:', err.message);
        res.writeHead(502, { 'Content-Type': 'application/json', ...corsHeaders() });
        res.end(JSON.stringify({ error: `Upstream error: ${err.message}` }));
      });

      proxyReq.write(body);
      proxyReq.end();
    });

    return;
  }

  // ── 404 ────────────────────────────────────────────────────────────────────
  res.writeHead(404, { 'Content-Type': 'application/json', ...corsHeaders() });
  res.end(JSON.stringify({ error: 'Not found.' }));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`✅ TikTok Shop Creator Proxy`);
  console.log(`   Port   : ${PORT}`);
  console.log(`   CORS   : * (all origins)`);
  console.log(`   Key    : per-request via x-api-key-fwd (never stored)`);
});
