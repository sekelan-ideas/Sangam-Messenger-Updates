# Independent crypto tests (add these next to tests/selftest.js)

- `independent_interop.py` runs the Windows engine against **python-axolotl**, a separately written, published Signal v3 library
  (NOT code written by us). This is the real interoperability check.
- `robustness_test.js` checks burst, ratchet, out-of-order, replay, tamper, and "no plaintext in the output".

    pip install python-axolotl python-axolotl-curve25519
    PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION=python python3 tests/independent_interop.py
    node tests/robustness_test.js

Both must pass after every change to app.js. The other tests/interop.py is a self-written Python reimplementation and must be labelled as such.
