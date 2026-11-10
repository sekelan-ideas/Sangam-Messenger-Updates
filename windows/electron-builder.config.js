// Builds two things:  npm run dist:setup  -> SangamSetup.exe (for GitHub / friends)
//                     npm run dist:store  -> Sangam.appx   (upload to Microsoft Partner Center)
const wantsStore = process.argv.some((a) => a === 'appx');
const identityName = process.env.STORE_IDENTITY_NAME;
const publisher = process.env.STORE_PUBLISHER;                 // e.g. CN=XXXXXXXX-XXXX-...
const publisherDisplayName = process.env.STORE_PUBLISHER_DISPLAY_NAME;

if (wantsStore && !(identityName && publisher && publisherDisplayName)) {
  throw new Error(
    'Set STORE_IDENTITY_NAME, STORE_PUBLISHER and STORE_PUBLISHER_DISPLAY_NAME ' +
    '(Partner Center > your app > Product management > Product identity) before building the Store package.'
  );
}

module.exports = {
  appId: 'com.sangam.messenger.desktop',
  productName: 'Sangam',
  copyright: 'Copyright (c) Sangam',
  directories: { output: 'dist', buildResources: 'build' },
  files: ['main.js', 'preload.js', 'package.json', 'app/**/*', 'build/icon.ico'],
  asar: true,
  win: { target: ['nsis'], icon: 'build/icon.ico', artifactName: 'Sangam-${version}-${arch}.${ext}' },
  nsis: {
    oneClick: false, perMachine: false, allowToChangeInstallationDirectory: true,
    createDesktopShortcut: true, createStartMenuShortcut: true, shortcutName: 'Sangam',
    artifactName: 'SangamSetup-${version}.${ext}', deleteAppDataOnUninstall: false
  },
  appx: {
    identityName, publisher, publisherDisplayName,
    applicationId: 'Sangam', displayName: 'Sangam',
    backgroundColor: '#0E1315', showNameOnTiles: true, languages: ['en-US'],
    artifactName: 'Sangam-${version}-Store.${ext}'
  }
};
