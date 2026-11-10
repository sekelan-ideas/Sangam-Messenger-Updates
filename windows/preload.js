'use strict';
const { contextBridge, ipcRenderer } = require('electron');
// The page can only read/write its own key vault, nothing else from the system.
contextBridge.exposeInMainWorld('sangamVault', {
  read: () => ipcRenderer.invoke('vault:read'),
  write: (data) => ipcRenderer.invoke('vault:write', data)
});
