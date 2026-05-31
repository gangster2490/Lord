require('dotenv').config();

const express = require('express');
const cors = require('cors');
const multer = require('multer');
const Anthropic = require('@anthropic-ai/sdk');

const app = express();
const PORT = process.env.PORT || 3001;
const ALLOWED_ORIGIN = process.env.ALLOWED_ORIGIN || 'http://localhost:5173';

// CORS — only allow the configured origin
app.use(cors({
  origin: ALLOWED_ORIGIN,
  methods: ['GET', 'POST'],
  allowedHeaders: ['Content-Type'],
}));

// Multer — memory storage, single image field
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 10 * 1024 * 1024 }, // 10 MB max
  fileFilter: (_req, file, cb) => {
    if (!file.mimetype.startsWith('image/')) {
      return cb(new Error('Only image files are allowed'));
    }
    cb(null, true);
  },
});

// Anthropic client — key read exclusively from env
const anthropic = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });

// Health check
app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

// Generate endpoint
app.post('/api/generate', upload.single('image'), async (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No image uploaded. Please provide an image file.' });
  }

  const { category = '', videoStyle = '', audience = '', tone = '' } = req.body;

  // Convert image buffer to base64
  const imageBase64 = req.file.buffer.toString('base64');
  const imageMediaType = req.file.mimetype;

  const systemPrompt = `Du bist ein erfahrener TikTok-Shop-Marketing-Experte für den deutschen Markt.
Deine Aufgabe ist es, virales, TikTok-konformes Marketingmaterial zu erstellen.

REGELN (zwingend):
- Keine Preise, keine Rabatte, keine falschen Versprechen
- TikTok-safe: keine irreführenden Claims, keine übertriebenen Superlative ohne Belang
- Alles auf Deutsch, außer dem Veo-Prompt (der ist auf Englisch)
- Antworte NUR mit gültigem JSON, kein erklärender Text, keine Markdown-Codeblöcke

Gib exakt dieses JSON-Format zurück:
{
  "title": "TikTok Titel mit Emoji, max 80 Zeichen",
  "hashtags": ["#Tag1","#Tag2","#Tag3","#Tag4","#Tag5"],
  "banner": ["Zeile 1 (max 30 Zeichen)","Zeile 2","Zeile 3","Call-to-Action"],
  "veo": "Full English Veo 3.1 prompt for a 10-second 9:16 vertical TikTok product video",
  "live": "0:00 | Hook\\n0:15 | Produktvorstellung\\n0:30 | Feature 1\\n0:45 | Feature 2\\n1:00 | Feature 3\\n1:15 | Nutzen\\n1:30 | Call-to-Action\\n1:45 | Abschluss"
}`;

  const userPrompt = `Analysiere das Produktbild und erstelle TikTok-Shop-Marketingmaterial für den deutschen Markt.

Kategorie: ${category || 'Allgemein'}
Video-Stil: ${videoStyle || 'Standard'}
Zielgruppe: ${audience || 'Allgemein'}
Ton: ${tone || 'Freundlich'}

Erstelle:
1. **title** – Fesselnder TikTok-Titel mit passendem Emoji, maximal 80 Zeichen
2. **hashtags** – Genau 5 deutsche TikTok-Hashtags (Mix aus Nischen- und Trend-Tags)
3. **banner** – 4 kurze Banner-Zeilen für TikTok-Overlay: 3 Produkt-Highlights + 1 Call-to-Action (jeweils max 30 Zeichen)
4. **veo** – Ausführlicher englischer Veo 3.1 Video-Prompt: 10-Sekunden, 9:16 Hochformat, beschreibe Kamerawinkel, Beleuchtung, Bewegung, Produktplatzierung, Hintergrund und Stimmung
5. **live** – 2-Minuten-Live-Skript mit Timestamps im Format "M:SS | Abschnitt", auf Deutsch

Antworte nur mit dem JSON-Objekt.`;

  try {
    const response = await anthropic.messages.create({
      model: 'claude-opus-4-8',
      max_tokens: 2048,
      system: systemPrompt,
      messages: [
        {
          role: 'user',
          content: [
            {
              type: 'image',
              source: {
                type: 'base64',
                media_type: imageMediaType,
                data: imageBase64,
              },
            },
            {
              type: 'text',
              text: userPrompt,
            },
          ],
        },
      ],
    });

    const rawText = response.content[0].text.trim();

    // Parse and validate JSON
    let parsed;
    try {
      // Strip possible markdown code fences
      const jsonStr = rawText.replace(/^```(?:json)?\n?/, '').replace(/\n?```$/, '').trim();
      parsed = JSON.parse(jsonStr);
    } catch {
      console.error('Failed to parse Claude response as JSON:', rawText);
      return res.status(500).json({ error: 'Failed to parse AI response. Please try again.' });
    }

    // Validate shape
    const required = ['title', 'hashtags', 'banner', 'veo', 'live'];
    for (const key of required) {
      if (!(key in parsed)) {
        return res.status(500).json({ error: `AI response missing field: ${key}` });
      }
    }

    return res.json(parsed);
  } catch (err) {
    console.error('Anthropic API error:', err.message);
    // Never expose the API key or full error details to the client
    return res.status(500).json({ error: 'AI generation failed. Please try again later.' });
  }
});

app.listen(PORT, () => {
  console.log(`TikTok Shop Creator backend running on port ${PORT}`);
  console.log(`Allowed origin: ${ALLOWED_ORIGIN}`);
});
