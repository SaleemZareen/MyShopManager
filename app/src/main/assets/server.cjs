"use strict";
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));

// server.ts
var import_express = __toESM(require("express"), 1);
var import_cors = __toESM(require("cors"), 1);
var import_path = __toESM(require("path"), 1);
var import_vite = require("vite");
var import_genai = require("@google/genai");
function pcmToWavBuffer(pcmBuffer, sampleRate = 24e3, numChannels = 1, bitsPerSample = 16) {
  const byteRate = sampleRate * numChannels * bitsPerSample / 8;
  const blockAlign = numChannels * bitsPerSample / 8;
  const dataSize = pcmBuffer.length;
  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + dataSize, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(numChannels, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(byteRate, 28);
  header.writeUInt16LE(blockAlign, 32);
  header.writeUInt16LE(bitsPerSample, 34);
  header.write("data", 36);
  header.writeUInt32LE(dataSize, 40);
  return Buffer.concat([header, pcmBuffer]);
}
async function startServer() {
  const app = (0, import_express.default)();
  const PORT = 3e3;
  app.use((0, import_cors.default)());
  app.use(import_express.default.json());
  const getAiClient = () => {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) return null;
    return new import_genai.GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          "User-Agent": "aistudio-build"
        }
      }
    });
  };
  const generateWithGemini = async (ai, requestParams) => {
    const candidateModels = [
      "gemini-3.5-flash",
      "gemini-flash-latest",
      "gemini-3.1-flash-lite-preview"
    ];
    let lastError = null;
    for (const model of candidateModels) {
      try {
        const response = await ai.models.generateContent({
          ...requestParams,
          model
        });
        if (response && response.text) {
          return response;
        }
      } catch (err) {
        lastError = err;
        const status = err.status || err.response && err.response.status || (err.message?.includes("503") ? 503 : void 0);
        if (status === 503 || status === 429) {
          await new Promise((resolve) => setTimeout(resolve, 200));
        }
      }
    }
    throw lastError || new Error("All Gemini models exhausted");
  };
  app.get("/api/health", (req, res) => {
    res.json({ status: "ok", aiConfigured: !!process.env.GEMINI_API_KEY });
  });
  app.get("/api/auth/google/callback", (req, res) => {
    res.send(`
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Google Sign-In Success</title>
  <style>
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      text-align: center;
      padding: 40px 20px;
      background-color: #f8fafc;
      color: #1e293b;
      margin: 0;
    }
    .card {
      background: white;
      padding: 32px 24px;
      border-radius: 24px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.05);
      display: inline-block;
      max-width: 440px;
      width: 100%;
      box-sizing: border-box;
      border: 1px solid #e2e8f0;
    }
    h1 {
      color: #10b981;
      font-size: 20px;
      margin-top: 0;
      margin-bottom: 12px;
    }
    p {
      font-size: 14px;
      color: #64748b;
      line-height: 1.6;
      margin: 8px 0;
    }
    .btn {
      display: inline-block;
      margin-top: 20px;
      padding: 12px 24px;
      background-color: #10b981;
      color: white;
      text-decoration: none;
      font-weight: 600;
      border-radius: 12px;
      font-size: 15px;
      box-shadow: 0 2px 8px rgba(16, 185, 129, 0.3);
    }
    .spinner {
      border: 3px solid #f3f3f3;
      border-top: 3px solid #10b981;
      border-radius: 50%;
      width: 28px;
      height: 28px;
      animation: spin 1s linear infinite;
      margin: 16px auto;
    }
    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }
  </style>
</head>
<body>
  <div class="card" id="mainCard">
    <h1>\u0633\u0627\u0626\u0646 \u0627\u0646 \u06A9\u0627\u0645\u06CC\u0627\u0628! (Sign-In Successful)</h1>
    <p>Connecting your Google Account to My Shop Manager...</p>
    <div class="spinner" id="loader"></div>
    <p style="font-size: 12px; color: #94a3b8;" id="statusText">Please wait, returning to My Shop Manager...</p>
    <div id="btnContainer" style="display: none;">
      <a href="#" id="returnBtn" class="btn">\u0627\u06CC\u067E \u0645\u06CC\u06BA \u0648\u0627\u067E\u0633 \u062C\u0627\u0626\u06CC\u06BA (Open App)</a>
    </div>
  </div>
  <script>
    try {
      const hash = window.location.hash || '';
      const search = window.location.search || '';
      const hashParams = new URLSearchParams(hash.replace(/^#/, ''));
      const searchParams = new URLSearchParams(search);
      
      const token = hashParams.get('access_token') || searchParams.get('access_token');
      const state = hashParams.get('state') || searchParams.get('state') || '';
      const isAndroid = /android/i.test(navigator.userAgent) || state === 'android_app';

      if (token) {
        const deepLinkUrl = "myshopmanager://oauth-callback#access_token=" + encodeURIComponent(token);
        const webReturnUrl = window.location.origin + "/#access_token=" + encodeURIComponent(token);
        const targetUrl = isAndroid ? deepLinkUrl : webReturnUrl;
        
        const returnBtn = document.getElementById('returnBtn');
        const btnContainer = document.getElementById('btnContainer');
        returnBtn.href = targetUrl;
        btnContainer.style.display = 'block';

        // Auto-redirect
        setTimeout(function() {
          window.location.href = targetUrl;
        }, 400);
      } else {
        const err = searchParams.get('error') || hashParams.get('error') || 'No access token found';
        document.getElementById('mainCard').innerHTML = '<h1>Sign-In Not Completed</h1><p>Error: ' + err + '</p>';
      }
    } catch (e) {
      document.getElementById('mainCard').innerHTML = '<h1>Error</h1><p>' + e.message + '</p>';
    }
  </script>
</body>
</html>
    `);
  });
  app.post("/api/ai/scan-business-card", async (req, res) => {
    try {
      const { image } = req.body;
      if (!image || typeof image !== "string") {
        return res.status(400).json({ error: "Image base64 data is required" });
      }
      const ai = getAiClient();
      if (!ai) {
        return res.status(503).json({ error: "AI Client not configured" });
      }
      let mimeType = "image/jpeg";
      let base64Data = image;
      if (image.startsWith("data:")) {
        const match = image.match(/^data:([^;]+);base64,(.*)$/);
        if (match) {
          mimeType = match[1];
          base64Data = match[2];
        }
      }
      const imagePart = {
        inlineData: {
          mimeType,
          data: base64Data
        }
      };
      const systemInstruction = `You are an expert OCR and data extractor. 
Your task is to analyze a business card (visiting card) image and extract supplier contact information.
Extract:
1. Supplier Company/Shop Name (shopName)
2. Contact Person's Name (contactPerson)
3. Contact Phone/Mobile Number (phone) - Clean Pakistani mobile number, e.g., 03001234567 or Roman representation
4. Email Address (email)
5. Shop/Business Address (address)

Return the extracted values as a JSON object matching the requested schema. If a value is missing or cannot be found, return an empty string for that property. Be extremely accurate. Do not make up values.`;
      const response = await generateWithGemini(ai, {
        contents: [
          imagePart,
          { text: "Extract supplier details from this visiting card." }
        ],
        config: {
          systemInstruction,
          responseMimeType: "application/json",
          responseSchema: {
            type: import_genai.Type.OBJECT,
            properties: {
              shopName: {
                type: import_genai.Type.STRING,
                description: "The company, shop, or agency name"
              },
              contactPerson: {
                type: import_genai.Type.STRING,
                description: "The owner, contact person, or supplier representative name"
              },
              phone: {
                type: import_genai.Type.STRING,
                description: "Mobile or phone number"
              },
              email: {
                type: import_genai.Type.STRING,
                description: "Email address if present"
              },
              address: {
                type: import_genai.Type.STRING,
                description: "Complete physical address"
              }
            },
            required: ["shopName", "contactPerson", "phone", "email", "address"]
          }
        }
      });
      const parsedJson = JSON.parse(response.text || "{}");
      return res.json({ success: true, parsed: parsedJson });
    } catch (err) {
      console.error("Scan card error:", err);
      return res.status(500).json({ error: err.message || "Failed to scan card" });
    }
  });
  app.post("/api/ai/parse-voice", async (req, res) => {
    try {
      const { text, isUrdu } = req.body;
      if (!text || typeof text !== "string") {
        return res.status(400).json({ error: "Text string is required" });
      }
      const ai = getAiClient();
      if (!ai) {
        return res.json({
          fallback: true,
          parsed: fallbackParseCommand(text)
        });
      }
      const systemInstruction = `You are an expert Pakistani Shop Accountant & Cashier AI Assistant ("Dukan Munshi").
Your task is to convert spoken Urdu, Roman Urdu, or English shop entries into structured transaction JSON for a retail store manager app.
Handle Urdu numbers (e.g. "\u067E\u0686\u0627\u0633 \u06C1\u0632\u0627\u0631" = 50000, "\u067E\u0627\u0646\u0686 \u0633\u0648" = 500, "\u0627\u06CC\u06A9 \u0644\u0627\u06A9\u06BE" = 100000), common Pakistani dukan terminology:
- "\u0633\u06CC\u0644\u0632", "\u0641\u0631\u0648\u062E\u062A", "\u0628\u06CC\u0686\u0627", "\u0633\u0648\u062F\u0627", "sale" => SALE
- "\u062E\u0631\u06CC\u062F\u0627\u0631\u06CC", "\u067E\u0631\u0686\u06CC\u0632", "\u0645\u0627\u0644 \u0644\u0627\u06CC\u0627", "\u062E\u0631\u06CC\u062F\u0627", "purchase" => PURCHASE
- "\u0627\u062E\u0631\u0627\u062C\u0627\u062A", "\u062E\u0631\u0686\u06C1", "\u06A9\u0631\u0627\u06CC\u06C1", "\u0686\u0627\u0626\u06D2", "\u0628\u062C\u0644\u06CC \u06A9\u0627 \u0628\u0644", "\u067E\u0679\u0631\u0648\u0644", "expense" => EXPENSE
- "\u0627\u062F\u06BE\u0627\u0631 \u0648\u0635\u0648\u0644", "\u06A9\u06BE\u0627\u062A\u06C1 \u062C\u0645\u0639", "\u067E\u06CC\u0633\u06D2 \u0648\u0635\u0648\u0644", "\u0631\u0642\u0645 \u0645\u0644\u06CC", "udhaar collect" => RECEIPT
- "\u0633\u067E\u0644\u0627\u0626\u0631 \u06A9\u0648 \u0627\u062F\u0627", "\u0627\u062F\u0627\u0626\u06CC\u06AF\u06CC", "\u0628\u0644 \u062F\u06CC\u0627", "supplier payment" => PAYMENT

Extract partyName (e.g. "\u0639\u0644\u06CC \u0628\u06BE\u0627\u0626\u06CC", "\u0646\u0633\u0644\u06D2 \u0688\u06CC\u067E\u0648"), category (e.g. "SHOP_RENT", "TEA", "UTILITIES", "STOCK_RESALE", "GENERAL_GOODS"), amount, and a clear notes string.`;
      const response = await generateWithGemini(ai, {
        contents: `Spoken Shop Command: "${text}"`,
        config: {
          systemInstruction,
          responseMimeType: "application/json",
          responseSchema: {
            type: import_genai.Type.OBJECT,
            properties: {
              type: {
                type: import_genai.Type.STRING,
                description: "Transaction type: SALE, PURCHASE, EXPENSE, RECEIPT, or PAYMENT"
              },
              amount: {
                type: import_genai.Type.NUMBER,
                description: "Total transaction amount in PKR"
              },
              partyName: {
                type: import_genai.Type.STRING,
                description: "Name of customer or supplier if mentioned, else empty string"
              },
              category: {
                type: import_genai.Type.STRING,
                description: "Category name such as General Goods, Stock Resale, SHOP_RENT, TEA, UTILITIES, MISCELLANEOUS"
              },
              notes: {
                type: import_genai.Type.STRING,
                description: "Clean English summary note"
              },
              urduSummary: {
                type: import_genai.Type.STRING,
                description: 'Concise Urdu confirmation text like "\u0641\u0631\u0648\u062E\u062A: 20,000 \u0631\u0648\u067E\u06D2 (\u0646\u0642\u062F)"'
              }
            },
            required: ["type", "amount", "notes", "urduSummary"]
          }
        }
      });
      const parsedJson = JSON.parse(response.text || "{}");
      return res.json({ success: true, parsed: parsedJson });
    } catch (err) {
      console.error("AI Parse Error:", err);
      return res.json({
        fallback: true,
        parsed: fallbackParseCommand(req.body?.text || "")
      });
    }
  });
  app.post("/api/ai/dukan-assistant", async (req, res) => {
    try {
      const { question, shopContext, isUrdu } = req.body;
      const ai = getAiClient();
      if (!ai) {
        return res.json({
          reply: isUrdu ? "\u0627\u06D2 \u0622\u0626\u06CC \u062F\u06A9\u0627\u0646 \u0628\u0632\u0646\u0633 \u0627\u06CC\u0688\u0648\u0627\u0626\u0632\u0631 \u0641\u0639\u0627\u0644 \u06C1\u06D2\u06D4 \u0622\u067E \u0645\u062C\u06BE \u0633\u06D2 \u062F\u06A9\u0627\u0646 \u06A9\u06CC \u0633\u06CC\u0644\u0632\u060C \u0645\u0646\u0627\u0641\u0639\u060C \u0627\u062F\u06BE\u0627\u0631 \u0648\u0635\u0648\u0644\u06CC \u0627\u0648\u0631 \u0645\u06CC\u0646\u062C\u0645\u0646\u0679 \u067E\u0631 \u0645\u0634\u0648\u0631\u06C1 \u0644\u06D2 \u0633\u06A9\u062A\u06D2 \u06C1\u06CC\u06BA\u06D4" : "Dukan AI Business Advisor is active. Ask me about sales growth, profit margins, udhaar recovery, and store management!"
        });
      }
      const prompt = `User Question: "${question}"
Current Shop Context: ${JSON.stringify(shopContext || {})}
Language Preference: ${isUrdu ? "Urdu (\u0627\u0631\u062F\u0648)" : "English"}`;
      const systemInstruction = `You are "Dukan AI Business Advisor & Senior Manager" (\u062F\u06A9\u0627\u0646 \u0627\u06D2 \u0622\u0626\u06CC \u0628\u0632\u0646\u0633 \u0645\u0634\u06CC\u0631 \u0648 \u0633\u06CC\u0646\u0626\u0631 \u0645\u06CC\u0646\u06CC\u062C\u0631).
You are an expert retail consultant, financial manager, and dukan advisor specifically for Pakistani shopkeepers, traders, and sole proprietors.

Your Responsibilities:
1. Provide actionable, practical retail management advice (increasing sales, calculating profit margins, managing inventory, reducing store expenses).
2. Give clear strategies for Udhaar (customer credit) recovery without hurting customer relationships.
3. Offer guidance on FBR tax compliance, POS integration, sales tax, and business registration for Pakistani retailers.
4. Analyze the provided Shop Context (Sales, Expenses, Udhaar, Inventory) to give personalized, data-backed insights.
5. Be respectful, encouraging, professional, and practical.
6. CRITICAL LANGUAGE MANDATE: If the user asks in Urdu (\u0627\u0631\u062F\u0648) or Roman Urdu, or if isUrdu is true, you MUST respond ONLY in fluent, clean, natural Urdu (\u0627\u0631\u062F\u0648 script). Match the exact language of the user question. Never reply in English if the user asked in Urdu!
7. REALISTIC URDU VOICE & PHONETIC MANDATE (CRITICAL FOR TTS PRONUNCIATION):
   - Write ALL numbers in spoken Urdu words! For example write "\u067E\u0627\u0646\u0686 \u06C1\u0632\u0627\u0631" instead of "5000", write "\u0628\u06CC\u0633 \u06C1\u0632\u0627\u0631" instead of "20000", write "\u062F\u0633 \u0641\u06CC\u0635\u062F" instead of "10%".
   - Write ALL English words/acronyms phonetically in Urdu script! For example write "\u0648\u0627\u0679\u0633 \u0627\u06CC\u067E", "\u0627\u06CC\u0641 \u0628\u06CC \u0622\u0631", "\u0627\u06CC\u0633 \u0627\u06CC\u0645 \u0627\u06CC\u0633", "\u067E\u0648\u0633", "\u0633\u06CC\u0644", "\u0633\u0679\u0627\u06A9", "\u067E\u0631\u0627\u0641\u0679" instead of English letters.
   - Do NOT use markdown symbols like asterisks (*), hashes (#), hyphens (-), bullets, or brackets in Urdu responses.
   - Use simple, natural conversational Pakistani Urdu as spoken by a polite, experienced Pakistani dukan senior advisor.
   - Keep sentences short and clear, separated by Urdu full stop (\u06D4) for smooth, natural human speech synthesis without robotic pauses.`;
      const response = await generateWithGemini(ai, {
        contents: prompt,
        config: {
          systemInstruction
        }
      });
      return res.json({ reply: response.text });
    } catch (err) {
      console.error("Dukan Assistant Error:", err);
      const isUrdu = !!req.body?.isUrdu;
      return res.json({
        reply: isUrdu ? "\u0645\u0639\u0630\u0631\u062A\u060C \u0627\u0633 \u0648\u0642\u062A \u0627\u06D2 \u0622\u0626\u06CC \u0633\u0631\u0648\u0631 \u067E\u0631 \u0645\u0635\u0631\u0648\u0641\u06CC\u062A \u0632\u06CC\u0627\u062F\u06C1 \u06C1\u06D2\u06D4 \u0628\u0631\u0627\u06C1 \u06A9\u0631\u0645 \u06A9\u0686\u06BE \u062F\u06CC\u0631 \u0628\u0639\u062F \u062F\u0648\u0628\u0627\u0631\u06C1 \u06A9\u0648\u0634\u0634 \u06A9\u0631\u06CC\u06BA \u06CC\u0627 \u0627\u067E\u0646\u06D2 \u0633\u0648\u0627\u0644 \u06A9\u0648 \u0645\u062E\u062A\u0635\u0631\u0627\u064B \u0644\u06A9\u06BE\u06CC\u06BA\u06D4" : "The AI server is experiencing high traffic right now. Please try again in a moment or rephrase your query."
      });
    }
  });
  app.post("/api/ai/gemini-speech-stream", async (req, res) => {
    try {
      const { text, isUrdu, voiceName } = req.body || {};
      const safeText = String(text || "").trim();
      if (!safeText) {
        return res.status(400).json({ error: "Text is required for speech" });
      }
      const ai = getAiClient();
      if (!ai) {
        return res.status(500).json({ error: "Gemini API key is not configured" });
      }
      res.setHeader("Content-Type", "text/event-stream");
      res.setHeader("Cache-Control", "no-cache, no-transform");
      res.setHeader("Connection", "keep-alive");
      res.setHeader("X-Accel-Buffering", "no");
      res.flushHeaders?.();
      const selectedVoice = voiceName || (isUrdu ? "Zephyr" : "Zephyr");
      const stream = await ai.models.generateContentStream({
        model: "gemini-3.1-flash-tts-preview",
        contents: [{ parts: [{ text: safeText }] }],
        config: {
          responseModalities: [import_genai.Modality.AUDIO],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: { voiceName: selectedVoice }
            }
          }
        }
      });
      for await (const chunk of stream) {
        const inlinePart = chunk.candidates?.[0]?.content?.parts?.[0]?.inlineData;
        if (inlinePart && inlinePart.data) {
          const payload = JSON.stringify({
            chunk: inlinePart.data,
            mimeType: inlinePart.mimeType || "audio/l16; rate=24000; channels=1",
            sampleRate: 24e3
          });
          res.write(`data: ${payload}

`);
        }
      }
      res.write("data: [DONE]\n\n");
      return res.end();
    } catch (err) {
      console.error("Gemini Native Audio Streaming Error:", err);
      if (!res.headersSent) {
        return res.status(500).json({ error: "Gemini Audio Streaming failed" });
      }
      res.write(`data: {"error": "${err.message || "Stream failed"}"}

`);
      return res.end();
    }
  });
  app.all(["/api/ai/gemini-speech", "/api/ai/tts"], async (req, res) => {
    try {
      const text = String(req.body?.text || req.query.text || "").trim();
      const isUrdu = req.body?.isUrdu !== void 0 ? !!req.body.isUrdu : String(req.query.lang || "").startsWith("ur") || /[\u0600-\u06FF]/.test(text);
      const voiceName = String(req.body?.voiceName || req.query.voice || "Zephyr");
      if (!text) {
        return res.status(400).json({ error: "Text is required" });
      }
      const ai = getAiClient();
      if (!ai) {
        return res.status(500).json({ error: "Gemini API key is not configured" });
      }
      const response = await ai.models.generateContent({
        model: "gemini-3.1-flash-tts-preview",
        contents: [{ parts: [{ text }] }],
        config: {
          responseModalities: [import_genai.Modality.AUDIO],
          speechConfig: {
            voiceConfig: {
              prebuiltVoiceConfig: { voiceName: voiceName || "Zephyr" }
            }
          }
        }
      });
      const inlinePart = response.candidates?.[0]?.content?.parts?.[0]?.inlineData;
      if (!inlinePart || !inlinePart.data) {
        return res.status(500).json({ error: "No audio returned from Gemini" });
      }
      const pcmBuffer = Buffer.from(inlinePart.data, "base64");
      const wavBuffer = pcmToWavBuffer(pcmBuffer, 24e3, 1, 16);
      const accepts = req.headers.accept || "";
      if (req.method === "GET" || accepts.includes("audio/") || req.query.format === "wav") {
        res.set({
          "Content-Type": "audio/wav",
          "Content-Length": wavBuffer.length.toString(),
          "Cache-Control": "public, max-age=86400"
        });
        return res.send(wavBuffer);
      }
      return res.json({
        audioBase64: inlinePart.data,
        sampleRate: 24e3,
        mimeType: inlinePart.mimeType || "audio/l16; rate=24000; channels=1"
      });
    } catch (err) {
      console.error("Gemini Native Speech Error:", err);
      return res.status(500).json({ error: "Gemini speech generation failed" });
    }
  });
  if (process.env.NODE_ENV !== "production") {
    const vite = await (0, import_vite.createServer)({
      server: { middlewareMode: true },
      appType: "spa"
    });
    app.use(vite.middlewares);
  } else {
    const distPath = import_path.default.join(process.cwd(), "dist");
    app.use(import_express.default.static(distPath));
    app.use((req, res) => {
      res.sendFile(import_path.default.join(distPath, "index.html"));
    });
  }
  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Dukan Manager Server running on http://0.0.0.0:${PORT}`);
  });
}
function fallbackParseCommand(text) {
  let type = "SALE";
  let category = "General Goods";
  let amount = 0;
  let partyName = "";
  const numMatch = text.match(/\d+/g);
  if (numMatch && numMatch.length > 0) {
    amount = parseInt(numMatch.join(""), 10);
  }
  if (!amount) {
    if (text.includes("\u067E\u0686\u0627\u0633 \u06C1\u0632\u0627\u0631") || text.includes("50k")) amount = 5e4;
    else if (text.includes("\u0628\u06CC\u0633 \u06C1\u0632\u0627\u0631") || text.includes("20k")) amount = 2e4;
    else if (text.includes("\u062F\u0633 \u06C1\u0632\u0627\u0631") || text.includes("10k")) amount = 1e4;
    else if (text.includes("\u067E\u0627\u0646\u0686 \u06C1\u0632\u0627\u0631") || text.includes("5k")) amount = 5e3;
    else if (text.includes("\u06C1\u0632\u0627\u0631") || text.includes("1k")) amount = 1e3;
    else if (text.includes("\u067E\u0627\u0646\u0686 \u0633\u0648")) amount = 500;
    else amount = 1e3;
  }
  if (text.includes("\u0627\u062F\u06BE\u0627\u0631 \u0648\u0635\u0648\u0644") || text.includes("\u06A9\u06BE\u0627\u062A\u06C1 \u062C\u0645\u0639") || text.includes("udhaar")) {
    type = "RECEIPT";
  } else if (text.includes("\u0633\u067E\u0644\u0627\u0626\u0631") || text.includes("\u0627\u062F\u0627\u0626\u06CC\u06AF\u06CC") || text.includes("supplier")) {
    type = "PAYMENT";
  } else if (text.includes("\u067E\u0631\u0686\u06CC\u0632") || text.includes("\u062E\u0631\u06CC\u062F\u0627\u0631\u06CC") || text.includes("purchase")) {
    type = "PURCHASE";
    category = "Stock Resale";
  } else if (text.includes("\u06A9\u0631\u0627\u06CC\u06C1") || text.includes("rent") || text.includes("\u0686\u0627\u0626\u06D2") || text.includes("\u062E\u0631\u0686") || text.includes("expense")) {
    type = "EXPENSE";
    if (text.includes("\u06A9\u0631\u0627\u06CC\u06C1")) category = "SHOP_RENT";
    else if (text.includes("\u0686\u0627\u0626\u06D2")) category = "TEA";
    else category = "MISCELLANEOUS";
  } else {
    type = "SALE";
  }
  return {
    type,
    amount,
    partyName,
    category,
    notes: `Voice Entry: "${text}"`,
    urduSummary: `${type === "SALE" ? "\u0641\u0631\u0648\u062E\u062A" : type === "EXPENSE" ? "\u062E\u0631\u0686\u06C1" : "\u0627\u0646\u062F\u0631\u0627\u062C"}: ${amount} \u0631\u0648\u067E\u06D2`
  };
}
startServer();
//# sourceMappingURL=server.cjs.map
