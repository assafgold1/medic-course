// Renders the cardiac-cycle animation (heart-cycle.html) to:
//   docs/images + content/images: cardio--anim-cycle.gif   (website, plays as a GIF)
//   content/images: cardio--anim-cycle.webp (a 5×6 grid of frames; the apps animate it, see TopicImage.frames)
// Run: node tools/infographics/heart-cycle.js
const fs = require('fs'), path = require('path'), os = require('os'), { execFileSync } = require('child_process');
const root = path.join(__dirname, '../..');
const chrome = process.env.CHROME || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const N = 30, COLS = 5, FRAME_MS = 120, SIZE = 540;
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'heart-'));
const html = path.join(__dirname, 'heart-cycle.html');
const frames = [];
for (let i = 0; i < N; i++) {
  const png = path.join(tmp, `f${String(i).padStart(2, '0')}.png`);
  execFileSync(chrome, ['--headless', '--disable-gpu', '--hide-scrollbars', '--force-device-scale-factor=1', '--window-size=720,720', `--screenshot=${png}`, `file://${html}?t=${i / N}`], { stdio: 'ignore' });
  frames.push(png);
}
const img = path.join(root, 'content/images');
const gif = path.join(img, 'cardio--anim-cycle.gif');
execFileSync('magick', ['-delay', String(FRAME_MS / 10), '-loop', '0', ...frames, '-resize', `${SIZE}x${SIZE}`, '-layers', 'Optimize', gif]);
const grid = path.join(img, 'cardio--anim-cycle.webp');
execFileSync('montage', [...frames, '-resize', `${SIZE}x${SIZE}`, '-tile', `${COLS}x`, '-geometry', '+0+0', '-quality', '82', grid]);
console.log('gif', (fs.statSync(gif).size / 1024).toFixed(0), 'KB; grid', (fs.statSync(grid).size / 1024).toFixed(0), 'KB');
