// Builds everything the app needs offline: compiled Tailwind CSS and bundled Inter fonts.
const { execFileSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');
const app = path.join(root, 'app');
fs.mkdirSync(path.join(app, 'styles'), { recursive: true });
fs.mkdirSync(path.join(app, 'fonts'), { recursive: true });

// 1) Fonts (Inter, latin) copied from the npm package, no network at runtime.
const weights = [400, 500, 600, 700, 800];
let css = '';
for (const w of weights) {
  const file = `inter-latin-${w}-normal.woff2`;
  fs.copyFileSync(path.join(root, 'node_modules', '@fontsource', 'inter', 'files', file),
                  path.join(app, 'fonts', file));
  css += `@font-face{font-family:'Inter';font-style:normal;font-display:swap;font-weight:${w};` +
         `src:url('${file}') format('woff2');}\n`;
}
fs.writeFileSync(path.join(app, 'fonts', 'fonts.css'), css);

// 2) Tailwind: compile only the classes the page uses (same engine the CDN ran at runtime).
const bin = path.join(root, 'node_modules', 'tailwindcss', 'lib', 'cli.js');
const input = path.join(root, 'src', 'tailwind.css');
execFileSync(process.execPath, [bin, '-i', input, '-o', path.join(app, 'styles', 'tailwind.css'),
  '--content', path.join(app, 'index.html') + ',' + path.join(app, 'app.js'), '--minify'], { stdio: 'inherit' });
console.log('assets ready');
