// Extracts the course content from docs/index.html into content/content.json
// and copies it to the server and the shared app. Run: node tools/export-content.js
const fs = require('fs'), path = require('path'), crypto = require('crypto');
const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'docs/index.html'), 'utf8');
const script = html.split('<script>').find(p => p.includes('/* ===================== CONFIG'));
const CFG = '/* ===================== CONFIG ===================== */', STO = '/* ===================== STORAGE ===================== */';
const dataPart = script.split(CFG)[0];
const cfg = script.split(CFG)[1].split(STO)[0]
  .replace(/const P = [\s\S]*?const PASS = (\d+);/, 'const PASS = $1;');
const TOPICS = new Function(dataPart + cfg + '; return {TOPICS, ORDER, GROUP_ORDER, CATS, SHORT, PASS};')();
// New topics written outside the site file (content/extra/_new-topics.json), placed after an existing topic.
const NEW_TOPICS = JSON.parse(fs.readFileSync(path.join(root, 'content/extra/_new-topics.json'), 'utf8'));
for (const n of NEW_TOPICS) { TOPICS.TOPICS.push(n); TOPICS.ORDER.splice(TOPICS.ORDER.indexOf(n.after) + 1, 0, n.id) }
const t = TOPICS.TOPICS.slice().sort((a,b)=>TOPICS.ORDER.indexOf(a.id)-TOPICS.ORDER.indexOf(b.id));

// Extra material reviewed from the course decks (content/extra/<topic>.json) and learning images
// (content/images, listed in content/extra/_image-picks.json). Appended after the site's own material.
const extraDir = path.join(root, 'content/extra');
const picks = JSON.parse(fs.readFileSync(path.join(extraDir, '_image-picks.json'), 'utf8'));
const imageFile = (tid, deck, img) => `${tid}--${deck}-${img}.webp`;
const EXTRA = {};
for (const x of t) {
  const f = path.join(extraDir, x.id + '.json');
  const e = fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : { sections: [], quiz: [] };
  const images = (picks[x.id] || []).map(([deck, img, caption]) => [imageFile(x.id, deck, img), caption])
    .filter(([file]) => fs.existsSync(path.join(root, 'content/images', file)));
  EXTRA[x.id] = { learn: e.sections.map(s => ({ h: s.h, steps: !!s.steps, items: s.items })), quiz: e.quiz, images };
}
const body = {
  passMark: TOPICS.PASS,
  categories: TOPICS.GROUP_ORDER.map(id => ({ id, name: TOPICS.CATS[id], short: TOPICS.SHORT[id] })),
  topics: t.map((x, i) => ({
    id: x.id, number: i + 1, cat: x.cat, title: x.title, blurb: x.blurb, note: x.note || null,
    nums: x.nums.map(([value, label]) => ({ value, label })),
    sections: x.learn.concat(EXTRA[x.id].learn).map(s => ({ title: s.h, steps: !!s.steps, items: s.items })),
    questions: x.quiz.concat(EXTRA[x.id].quiz).map((q, k) => ({ id: `${x.id}#${k}`, q: q[0], options: q[1], explanation: q[2] })),
    images: EXTRA[x.id].images.map(([file, caption]) => ({ file, caption })),
  })),
};
const PREFIX = /^[והבלמשכ]{1,2}(?=[\u05d0-\u05ea]{3})/;
const words = s => new Set(String(s).replace(/[^\u05d0-\u05eaA-Za-z0-9 ]/g, ' ').split(/\s+/)
  .filter(w => w.length > 1).flatMap(w => [w.toLowerCase(), w.replace(PREFIX, '')]));
// Images go inside the text: each one is placed after the section whose text matches its caption best.
// Interactive anatomy points (content/extra/_hotspots.json) ride along as image.spots [[x%, y%, name, info], ...].
const HOT = JSON.parse(fs.readFileSync(path.join(extraDir, '_hotspots.json'), 'utf8'));
for (const topic of body.topics) {
  for (const im of topic.images) if (HOT[im.file]) im.spots = HOT[im.file].map(([x, y, name, info]) => ({ x, y, name, info }));
  const secWords = topic.sections.map(sec => words(sec.title + ' ' + sec.items.join(' ')));
  for (const sec of topic.sections) sec.images = [];
  topic.images.forEach((im, k) => {
    const iw = words(im.caption);
    let best = -1, bestScore = 0;
    secWords.forEach((sw, i) => { let sc = 0; for (const w of iw) if (sw.has(w)) sc++; if (sc > bestScore) { best = i; bestScore = sc } });
    if (best < 0) best = Math.min(topic.sections.length - 1, Math.floor(k * topic.sections.length / topic.images.length));
    if (best >= 0) topic.sections[best].images.push(im.file);
  });
}

// One image per question: the topic image whose caption shares the most words with the question,
// its correct answer and explanation; ties go to the image used least so far, to spread them out.
const QIMG = {};
for (const topic of body.topics) {
  const imgs = topic.images.map(im => ({ file: im.file, w: words(im.caption), used: 0 }));
  if (!imgs.length) continue;
  for (const q of topic.questions) {
    const qw = words(q.q + ' ' + q.options[0] + ' ' + q.explanation);
    let best = null, bestScore = -1;
    for (const im of imgs) {
      let score = 0; for (const w of im.w) if (qw.has(w)) score++;
      if (score > bestScore || (score === bestScore && im.used < best.used)) { best = im; bestScore = score }
    }
    best.used++; q.image = best.file; QIMG[q.id] = best.file;
  }
}

// reference tables from the "numbers" screen
const rowsSrc = script.match(/const rows = (\[[\s\S]*?\]);\n  const gen/)[1];
const genSrc = script.match(/const gen = (\[[\s\S]*?\]);\n  \$app/)[1];
const strip = v => String(v).replace(/<[^>]+>/g, '');
const M = x => x;
body.reference = {
  compare: { columns: ['מבוגר', 'ילד', 'תינוק'], rows: new Function('M', 'return ' + rowsSrc)(M).map(r => ({ label: strip(r[0]), values: r.slice(1).map(strip) })) },
  general: new Function('M', 'return ' + genSrc)(M).map(r => ({ label: strip(r[0]), value: strip(r[1]) })),
};
const version = crypto.createHash('sha256').update(JSON.stringify(body)).digest('hex').slice(0, 12);
const out = JSON.stringify({ version, ...body });
for (const p of ['content/content.json', 'server/src/main/resources/content.json', 'shared/src/commonMain/composeResources/files/content.json']) {
  fs.mkdirSync(path.dirname(path.join(root, p)), { recursive: true });
  fs.writeFileSync(path.join(root, p), out);
}
// Images ship inside the apps and next to the website; the site merges EXTRA at startup (docs/extra.js).
const imgSrc = path.join(root, 'content/images');
for (const dir of ['shared/src/commonMain/composeResources/files/images', 'docs/images']) {
  const out = path.join(root, dir);
  fs.rmSync(out, { recursive: true, force: true });
  fs.mkdirSync(out, { recursive: true });
  for (const f of fs.readdirSync(imgSrc)) fs.copyFileSync(path.join(imgSrc, f), path.join(out, f));
}
const SITE = Object.fromEntries(body.topics.map(t => [t.id, { secImgs: t.sections.map(s => s.images), spots: Object.fromEntries(t.images.filter(i => i.spots).map(i => [i.file, i.spots])) }]));
fs.writeFileSync(path.join(root, 'docs/extra.js'), '// Generated by tools/export-content.js\nwindow.EXTRA = ' + JSON.stringify(EXTRA) + ';\nwindow.QIMG = ' + JSON.stringify(QIMG) + ';\nwindow.LAYOUT = ' + JSON.stringify(SITE) + ';\nwindow.NEW_TOPICS = ' + JSON.stringify(NEW_TOPICS) + ';\n');
console.log(`version ${version}: ${body.topics.length} topics, ${body.topics.reduce((s,x)=>s+x.questions.length,0)} questions, ${body.topics.reduce((s,x)=>s+x.images.length,0)} images, ${(out.length/1024).toFixed(0)} KB`);
