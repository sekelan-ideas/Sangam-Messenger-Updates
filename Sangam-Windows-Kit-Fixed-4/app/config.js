// Fill these in before you publish (the Store reviewers and your users will see them).
// Flip ENCRYPTION_VERIFIED to true ONLY after the real-device acceptance test plan passes.
window.SANGAM_CONFIG = {
  privacyUrl: "",   // e.g. "https://yourname.github.io/sangam/privacy.html"
  supportEmail: "", // e.g. "support@yourdomain.com"
  ENCRYPTION_VERIFIED: false,

  get SHORT_ENCRYPTION_LABEL() {
    return this.ENCRYPTION_VERIFIED
      ? "End-to-end encrypted"
      : "Signal Protocol sessions, sent over TLS";
  },

  get FULL_ENCRYPTION_LABEL() {
    return this.ENCRYPTION_VERIFIED
      ? "End-to-end encrypted: message, media and call content only the people in the chat can read. Profile info and who you talk to are visible to Sangam."
      : "Signal Protocol sessions, sent over TLS";
  },

  get CALL_OVERLAY_LABEL() {
    return this.ENCRYPTION_VERIFIED
      ? "🔒 Sangam Confluence Call · End-to-end encrypted"
      : "🔒 Sangam Confluence Call · Signal Protocol sessions, sent over TLS";
  },

  get CALL_ACTIVE_TAG() {
    return this.ENCRYPTION_VERIFIED ? "🔒 End-to-end encrypted" : "🔒 Signal Protocol";
  },

  get QUICK_DIAL_TITLE() {
    return this.ENCRYPTION_VERIFIED
      ? "Confluence Orbit · Quick Dial (End-to-end encrypted)"
      : "Confluence Orbit · Quick Dial (Signal Protocol)";
  }
};
