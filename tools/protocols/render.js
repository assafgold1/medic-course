// Renders each protocol in data.js to a flowchart image: content/images/<topic>--proto-<id>.webp,
// and lists it first in that topic's _image-picks.json entry. Run: node tools/protocols/render.js
const fs = require('fs'), path = require('path'), os = require('os'), { execFileSync } = require('child_process');
const root = path.join(__dirname, '../..');
const protocols = require('./data');
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'proto-'));
const esc = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;');

const css = `
*{box-sizing:border-box;margin:0}
body{width:900px;background:#fff;font-family:-apple-system,"SF Hebrew","Arial Hebrew",Arial,sans-serif;color:#2a1a10;direction:rtl;padding:36px}
.card{border-radius:34px;background:#fff7f1;border:3px solid #f5d2bb;padding:30px 34px 34px}
.top{display:flex;align-items:center;gap:14px;margin-bottom:22px}
.chip{background:#ea580c;color:#fff;border-radius:999px;padding:6px 16px;font-size:20px;font-weight:800}
h1{font-size:38px;font-weight:900;color:#9a3412;line-height:1.2}
.flow{display:flex;flex-direction:column;align-items:stretch}
.arrow{align-self:center;width:4px;height:26px;background:#f0a477;position:relative;margin:2px 0 10px}
.arrow:after{content:"";position:absolute;bottom:-10px;left:-8px;border:10px solid transparent;border-top:12px solid #f0a477;border-bottom:0}
.step{display:flex;gap:18px;align-items:center;background:#fff;border:2px solid #fbe0cf;border-radius:22px;padding:14px 20px}
.n{flex:none;width:50px;height:50px;border-radius:50%;background:#ea580c;color:#fff;font-size:26px;font-weight:900;display:flex;align-items:center;justify-content:center}
.h{font-size:26px;font-weight:800;line-height:1.25}.d{font-size:21px;color:#6b4a38;margin-top:3px;line-height:1.35}
.alarm{background:#b91c1c;color:#fff;border-radius:22px;padding:16px 22px;font-size:25px;font-weight:800;line-height:1.35;display:flex;gap:14px;align-items:center}
.alarm .ic{font-size:34px}
.stop{background:#fee2e2;border:3px solid #b91c1c;border-radius:22px;padding:14px 20px;font-size:24px;font-weight:800;color:#7f1d1d;line-height:1.35;display:flex;gap:14px;align-items:center}
.stop .ic{font-size:30px}
.note{background:#ffedd5;border-radius:20px;padding:14px 20px;font-size:21px;font-weight:600;color:#7c2d12;line-height:1.4;margin-top:6px}
.q{align-self:center;background:#2a1a10;color:#fff;border-radius:22px;padding:14px 26px;font-size:25px;font-weight:800;text-align:center;line-height:1.3;max-width:92%}
.br{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-top:14px}
.b{background:#fff;border-radius:22px;border:3px solid #93c5fd;padding:12px 18px 14px;position:relative}
.b.no{border-color:#c4b5fd}
.yn{display:inline-block;border-radius:999px;padding:3px 14px;font-size:19px;font-weight:900;color:#fff;background:#2563eb;margin-bottom:6px}
.no .yn{background:#7c3aed}
.bh{font-size:23px;font-weight:800;margin-bottom:4px;line-height:1.25}
ul{padding-right:24px;font-size:21px;line-height:1.45}`;

const node = (s, i) => {
  if (s.k === 'alarm') return `<div class="alarm"><span class="ic">⚠️</span><span>${esc(s.h)}</span></div>`;
  if (s.k === 'stop') return `<div class="stop"><span class="ic">✋</span><span>${esc(s.h)}</span></div>`;
  if (s.k === 'note') return `<div class="note">💡 ${esc(s.h)}</div>`;
  if (s.k === 'if') {
    const b = (x, cls, yn) => `<div class="b ${cls}"><span class="yn">${yn}</span><div class="bh">${esc(x.h)}</div><ul>${x.items.map(t => `<li>${esc(t)}</li>`).join('')}</ul></div>`;
    return `<div class="q">${esc(s.h)}</div><div class="br">${b(s.yes, 'yes', 'כן')}${b(s.no, 'no', 'לא')}</div>`;
  }
  return `<div class="step"><div class="n">${i}</div><div><div class="h">${esc(s.h)}</div>${s.d ? `<div class="d">${esc(s.d)}</div>` : ''}</div></div>`;
};

const html = p => {
  let n = 0, out = [];
  p.steps.forEach((s, i) => {
    if (s.k === 'step') n++;
    if (i > 0 && s.k !== 'note') out.push('<div class="arrow"></div>');
    out.push(node(s, n));
  });
  return `<!doctype html><meta charset="utf-8"><style>${css}</style><div class="card"><div class="top"><span class="chip">פרוטוקול</span><h1>${esc(p.title)}</h1></div><div class="flow">${out.join('')}</div></div>`;
};

const picksFile = path.join(root, 'content/extra/_image-picks.json');
const picks = JSON.parse(fs.readFileSync(picksFile, 'utf8'));
const wordsFile = path.join(root, 'content/extra/_image-words.json');
const words = JSON.parse(fs.readFileSync(wordsFile, 'utf8'));
for (const t in picks) picks[t] = picks[t].filter(x => x[0] !== 'proto');
const only = process.argv[2];
for (const p of protocols) {
  const file = `${p.topic}--proto-${p.id}.webp`, out = path.join(root, 'content/images', file);
  if (!only || only === p.id) {
    const h = path.join(tmp, p.id + '.html'), png = path.join(tmp, p.id + '.png');
    fs.writeFileSync(h, html(p));
    execFileSync(chrome, ['--headless', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1', `--screenshot=${png}`, '--window-size=900,3200', 'file://' + h], { stdio: 'ignore' });
    execFileSync('magick', [png, '-trim', '+repage', '-bordercolor', 'white', '-border', '24', '-quality', '86', out]);
    console.log('rendered', file);
  }
  // Protocols lead their topic's images.
  (picks[p.topic] = picks[p.topic] || []).unshift(['proto', p.id, `פרוטוקול: ${p.title}`]);
  words[file] = [p.title, ...p.steps.flatMap(s => [s.h, s.d || '', ...(s.yes ? [s.yes.h, ...s.yes.items, s.no.h, ...s.no.items] : [])])].join(' ');
}
fs.writeFileSync(picksFile, JSON.stringify(picks, null, 1) + '\n');
fs.writeFileSync(wordsFile, JSON.stringify(words, null, 1) + '\n');
