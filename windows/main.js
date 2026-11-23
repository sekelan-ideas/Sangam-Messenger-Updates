'use strict';
// Sangam for Windows - app shell. Hosts the Sangam client UI in a locked-down window.
const { app, BrowserWindow, Menu, ipcMain, protocol, session, shell, safeStorage, net } = require('electron');
const path = require('path');
const fs = require('fs');
const { pathToFileURL } = require('url');

const SCHEME = 'sangam-app';
const ORIGIN = `${SCHEME}://app`;
const APP_DIR = path.join(__dirname, 'app');

protocol.registerSchemesAsPrivileged([
  { scheme: SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true } }
]);

if (!app.requestSingleInstanceLock()) { app.quit(); }

let win = null;

// ---------- key vault: Windows DPAPI via Electron safeStorage ----------
function vaultPath() { return path.join(app.getPath('userData'), 'signal_vault.bin'); }

// Node reports origin 'null' for custom schemes, so compare scheme + host explicitly.
function isAppUrl(url) {
  try { const u = new URL(url); return u.protocol === `${SCHEME}:` && u.host === 'app'; } catch { return false; }
}
function isTrusted(event) { return isAppUrl(event.senderFrame && event.senderFrame.url); }

ipcMain.handle('vault:read', (event) => {
  if (!isTrusted(event)) throw new Error('untrusted sender');
  if (!safeStorage.isEncryptionAvailable() || !fs.existsSync(vaultPath())) return null;
  try { return JSON.parse(safeStorage.decryptString(fs.readFileSync(vaultPath()))); }
  catch { return null; }
});

ipcMain.handle('vault:write', (event, data) => {
  if (!isTrusted(event)) throw new Error('untrusted sender');
  if (!safeStorage.isEncryptionAvailable()) throw new Error('Windows secure storage unavailable');
  const tmp = vaultPath() + '.tmp';
  fs.mkdirSync(path.dirname(tmp), { recursive: true });
  fs.writeFileSync(tmp, safeStorage.encryptString(JSON.stringify(data)));
  fs.renameSync(tmp, vaultPath());
  return true;
});

// ---------- window ----------
function createWindow() {
  win = new BrowserWindow({
    width: 1280, height: 820, minWidth: 980, minHeight: 620,
    title: 'Sangam', backgroundColor: '#0E1315', autoHideMenuBar: true, show: false,
    icon: path.join(__dirname, 'build', 'icon.ico'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true, sandbox: true, nodeIntegration: false,
      webSecurity: true, devTools: !app.isPackaged, spellcheck: true
    }
  });
  win.once('ready-to-show', () => win.show());
  win.loadURL(`${ORIGIN}/index.html`);

  // Never navigate away from the app; open web links in the user's browser.
  win.webContents.on('will-navigate', (e, url) => { if (!isAppUrl(url)) e.preventDefault(); });
  win.webContents.setWindowOpenHandler(({ url }) => {
    try { const p = new URL(url).protocol; if (p === 'https:' || p === 'mailto:') shell.openExternal(url); } catch {}
    return { action: 'deny' };
  });
  win.on('closed', () => { win = null; });
}

app.whenReady().then(() => {
  if (!process.windowsStore) app.setAppUserModelId('com.sangam.messenger.desktop');

  // Serve ./app over a secure custom origin, with a path-traversal guard.
  protocol.handle(SCHEME, (request) => {
    const rel = decodeURIComponent(new URL(request.url).pathname).replace(/^\/+/, '') || 'index.html';
    const file = path.normalize(path.join(APP_DIR, rel));
    if (!file.startsWith(APP_DIR + path.sep)) return new Response('forbidden', { status: 403 });
    return net.fetch(pathToFileURL(file).toString());
  });

  // Only camera/microphone (calls), notifications and clipboard writes, and only for our own origin.
  const allowed = new Set(['media', 'notifications', 'clipboard-sanitized-write']);
  session.defaultSession.setPermissionRequestHandler((wc, permission, cb, details) => {
    cb(allowed.has(permission) && isAppUrl(details.requestingUrl));
  });
  session.defaultSession.setPermissionCheckHandler((wc, permission, origin) => allowed.has(permission) && isAppUrl(origin));

  Menu.setApplicationMenu(Menu.buildFromTemplate([
    { label: 'Edit', submenu: [{ role: 'undo' }, { role: 'redo' }, { type: 'separator' },
      { role: 'cut' }, { role: 'copy' }, { role: 'paste' }, { role: 'selectAll' }] },
    { label: 'View', submenu: [{ role: 'resetZoom' }, { role: 'zoomIn' }, { role: 'zoomOut' },
      { type: 'separator' }, { role: 'togglefullscreen' }] }
  ]));
  createWindow();
});

app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.on('window-all-closed', () => app.quit());
app.on('web-contents-created', (_e, contents) => { contents.on('will-attach-webview', (ev) => ev.preventDefault()); });
