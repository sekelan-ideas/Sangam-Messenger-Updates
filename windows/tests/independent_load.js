const { createAppContext } = require('./selftest.js');
module.exports = { make: () => createAppContext(async () => { throw new Error('offline'); }).SangamCrypto };
