// Renders the cardiac-cycle animation to:
//   content/images/cardio--anim-cycle.gif  (website, plays as a GIF)
//   content/images/cardio--anim-cycle.webp (a 5×5 grid of frames; the apps animate it, see TopicImage.frames)
// Heart footage: src/cardiac-cycle.gif ("Cardiac Cycle Animated", Wikimedia Commons, CC BY-SA 4.0).
// It loops two beats in 50 frames; one beat is source frames 6–30. We crop the heart, reorder the beat
// to start at diastole (16–30, then 6–15) and lay Hebrew text over it in heart-cycle.html.
// Run: node tools/infographics/heart-cycle.js
const fs = require('fs'), path = require('path'), os = require('os'), { execFileSync } = require('child_process');
const root = path.join(__dirname, '../..');
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const COLS = 5, FRAME_MS = 80, SIZE = 540;
const ORDER = [...Array(15).keys()].map(k => 16 + k).concat([...Array(10).keys()].map(k => 6 + k));
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'heart-'));
execFileSync('magick', [path.join(__dirname, 'src/cardiac-cycle.gif'), '-coalesce', path.join(tmp, 's%03d.png')]);
const html = path.join(__dirname, 'heart-cycle.html');
const frames = ORDER.map((src, i) => {
  const crop = path.join(tmp, `h${i}.png`);
  execFileSync('magick', [path.join(tmp, `s${String(src).padStart(3, '0')}.png`), '-crop', '470x510+180+90', '+repage', crop]);
  const png = path.join(tmp, `f${String(i).padStart(2, '0')}.png`);
  execFileSync(chrome, ['--headless', '--disable-gpu', '--hide-scrollbars', '--allow-file-access-from-files', '--force-device-scale-factor=1', '--window-size=720,720',
    `--screenshot=${png}`, `file://${html}?i=${i}&n=${ORDER.length}&img=${encodeURIComponent('file://' + crop)}`], { stdio: 'ignore' });
  return png;
});
const img = path.join(root, 'content/images');
const gif = path.join(img, 'cardio--anim-cycle.gif');
execFileSync('magick', ['-delay', String(FRAME_MS / 10), '-loop', '0', ...frames, '-resize', `${SIZE}x${SIZE}`, '-layers', 'Optimize', gif]);
const grid = path.join(img, 'cardio--anim-cycle.webp');
execFileSync('montage', [...frames, '-resize', `${SIZE}x${SIZE}`, '-tile', `${COLS}x`, '-geometry', '+0+0', '-quality', '82', grid]);
const anim = path.join(root, 'content/extra/_animations.json');
const a = JSON.parse(fs.readFileSync(anim, 'utf8'));
a['cardio--anim-cycle.webp'] = { frames: ORDER.length, cols: COLS, frameMs: FRAME_MS };
fs.writeFileSync(anim, JSON.stringify(a, null, 1) + '\n');
console.log('gif', (fs.statSync(gif).size / 1024).toFixed(0), 'KB; grid', (fs.statSync(grid).size / 1024).toFixed(0), 'KB');
