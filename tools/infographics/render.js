// Renders tools/infographics/cards.js to content/images/<topic>--gfx-<name>.webp with headless Chrome,
// and adds each one to content/extra/_image-picks.json. Run: node tools/infographics/render.js
const fs = require('fs'), path = require('path'), os = require('os'), { execFileSync } = require('child_process');
const root = path.join(__dirname, '../..');
const cards = require('./cards');
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'gfx-'));
const css = `
*{box-sizing:border-box;margin:0}
body{width:900px;background:#fff;font-family:-apple-system,"SF Hebrew","Arial Hebrew",Arial,sans-serif;color:#2a1a10;direction:rtl;padding:36px}
.card{border-radius:34px;background:#fff7f1;border:3px solid #f5d2bb;padding:34px;display:flex;flex-direction:column;gap:22px}
h1{font-size:40px;font-weight:800;color:#c2410c;line-height:1.2}
.tiles{display:grid;gap:16px}.c2{grid-template-columns:1fr 1fr}.c3{grid-template-columns:1fr 1fr 1fr}.c4{grid-template-columns:repeat(4,1fr)}
.tile{background:#fff;border-radius:24px;padding:20px 18px;border:2px solid #fbe0cf;text-align:center}
.big{font-size:40px;font-weight:800;color:#ea580c;line-height:1.15}
.lbl{font-size:24px;font-weight:700;margin-top:6px}.sub{font-size:20px;color:#7a5a48;margin-top:4px;line-height:1.3}
table{width:100%;border-collapse:separate;border-spacing:0;background:#fff;border-radius:24px;overflow:hidden;border:2px solid #fbe0cf;font-size:23px}
th,td{padding:15px 14px;text-align:center;border-bottom:2px solid #fdeee4;line-height:1.3}
tr:last-child th,tr:last-child td{border-bottom:0}
tr:first-child th{background:#ea580c;color:#fff;font-size:25px}
th.rh{background:#fff1e7;color:#9a3412;font-weight:800;text-align:right}
.flow{display:flex;flex-direction:column;align-items:stretch}
.step{display:flex;gap:18px;align-items:center;background:#fff;border:2px solid #fbe0cf;border-radius:24px;padding:16px 20px}
.n{flex:none;width:52px;height:52px;border-radius:50%;background:#ea580c;color:#fff;font-size:28px;font-weight:800;display:flex;align-items:center;justify-content:center}
.st{font-size:27px;font-weight:800}.sd{font-size:21px;color:#7a5a48;margin-top:3px}
.arrow{text-align:center;color:#f0a477;font-size:22px;line-height:1.4}
.branch{display:flex;flex-direction:column;gap:14px}
.bq{background:#ea580c;color:#fff;border-radius:24px;padding:18px;text-align:center;font-size:28px;font-weight:800}
.bopts{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.bo{background:#fff;border-radius:24px;padding:18px 20px;border:3px solid #fbe0cf}
.bo.ok{border-color:#86d3a5}.bo.bad{border-color:#f29b9b}
.bh{font-size:25px;font-weight:800;margin-bottom:8px}.bo.ok .bh{color:#15803d}.bo.bad .bh{color:#b91c1c}
ul{padding-right:26px;font-size:22px;line-height:1.5}
.dd{display:grid;grid-template-columns:1fr 1fr;gap:16px}
.do,.dont{background:#fff;border-radius:24px;padding:18px 20px;border:3px solid #86d3a5}.dont{border-color:#f29b9b}
.ddh{font-size:27px;font-weight:800;color:#15803d;margin-bottom:8px}.dont .ddh{color:#b91c1c}
.letters{display:flex;flex-direction:column;gap:14px}
.let{display:flex;gap:22px;align-items:center;background:#fff;border:2px solid #fbe0cf;border-radius:24px;padding:14px 20px}
.L{flex:none;width:84px;height:84px;border-radius:22px;background:#ea580c;color:#fff;font-size:54px;font-weight:900;display:flex;align-items:center;justify-content:center;direction:ltr}
.lh{font-size:28px;font-weight:800}.ld{font-size:22px;color:#7a5a48;margin-top:3px}
.alarm{background:#b91c1c;color:#fff;border-radius:22px;padding:18px;text-align:center;font-size:32px;font-weight:900}
.stop{background:#fee2e2;border:3px solid #b91c1c;border-radius:22px;padding:18px 20px;font-size:26px;font-weight:800;color:#7f1d1d;line-height:1.45;text-align:center}
.note{background:#ffedd5;border-radius:20px;padding:16px 20px;font-size:22px;font-weight:600;color:#7c2d12;line-height:1.4}`;

const picksFile = path.join(root, 'content/extra/_image-picks.json');
const picks = JSON.parse(fs.readFileSync(picksFile, 'utf8'));
const wordsFile = path.join(root, 'content/extra/_image-words.json'), textOf = {};
for (const c of cards) {
  const html = `<!doctype html><meta charset="utf-8"><style>${css}</style><div class="card"><h1>${c.title || c.caption.split(":")[0]}</h1>${c.body}</div>`;
  const h = path.join(tmp, c.name + '.html'), png = path.join(tmp, c.topic + c.name + '.png');
  fs.writeFileSync(h, html);
  execFileSync(chrome, ['--headless', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1', `--screenshot=${png}`, '--window-size=900,2400', 'file://' + h], { stdio: 'ignore' });
  for (const topic of [c.topic, ...(c.also || [])]) {
    const out = path.join(root, 'content/images', `${topic}--gfx-${c.name}.webp`);
    execFileSync('magick', [png, '-trim', '+repage', '-bordercolor', 'white', '-border', '24', '-quality', '86', out]);
    const list = picks[topic] = (picks[topic] || []).filter(p => !(p[0] === 'gfx' && p[1] === c.name));
    // The scene-safety card leads its topics; the rest follow the deck images.
    textOf[`${topic}--gfx-${c.name}.webp`] = ((c.title || '') + ' ' + c.body).replace(/<[^>]+>/g, ' ').replace(/&[a-z]+;/g, ' ').replace(/\s+/g, ' ').trim();
    if (c.first) list.unshift(['gfx', c.name, c.caption]); else list.push(['gfx', c.name, c.caption]);
    console.log('rendered', path.basename(out));
  }
}
fs.writeFileSync(picksFile, JSON.stringify(picks, null, 1) + '\n');
// The text inside each diagram helps match it to questions (tools/export-content.js).
fs.writeFileSync(wordsFile, JSON.stringify(textOf, null, 1) + '\n');
