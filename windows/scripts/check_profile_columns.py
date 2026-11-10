#!/usr/bin/env python3
"""
Scans Android (Kotlin) and Windows (windows/app/app.js) for:
  1. Any `profiles` SELECT that uses `select=*` / `Columns.ALL` or a column outside
     the granted list:
     (id, username, display_name, created_at, avatar_url, last_seen_at,
      typing_for, typing_until, status)
  2. Any pre-login access to `profiles` (e.g. usernameTaken / lookupEmailByUsername
     querying `profiles` directly instead of `username_available` RPC or `login-lookup`
     Edge Function).
"""

import os
import re
import sys

GRANTED_COLUMNS = {
    "id",
    "username",
    "display_name",
    "created_at",
    "avatar_url",
    "last_seen_at",
    "typing_for",
    "typing_until",
    "status",
}

HERE = os.path.dirname(os.path.abspath(__file__))
PARENT = os.path.abspath(os.path.join(HERE, ".."))
if os.path.isdir(os.path.join(PARENT, "app", "src", "main")):
    MAIN_ROOT = PARENT
    WIN_APP_JS = os.path.join(PARENT, "windows", "app", "app.js")
else:
    WIN_APP_JS = os.path.join(PARENT, "app", "app.js")
    MAIN_ROOT = os.path.abspath(os.path.join(PARENT, "..", "..", "main", "Sangam-main"))


def scan_kotlin(main_root):
    violations = []
    checked = []
    kt_root = os.path.join(main_root, "app", "src", "main", "java")
    if not os.path.isdir(kt_root):
        return checked, violations

    # Resolve named column constants in Sangam.kt
    sangam_kt = os.path.join(kt_root, "com", "sangam", "messenger", "data", "Sangam.kt")
    col_constants = {}
    if os.path.isfile(sangam_kt):
        with open(sangam_kt, "r", encoding="utf-8") as f:
            src = f.read()
        for m in re.finditer(r"val\s+([A-Z0-9_]+)\s*=\s*Columns\.list\(([^)]+)\)", src, re.DOTALL):
            name = m.group(1)
            cols = re.findall(r'"([a-zA-Z0-9_]+)"', m.group(2))
            col_constants[name] = cols

        # Verify pre-login usernameTaken uses RPC username_available, NOT client.from("profiles")
        ut_match = re.search(r"suspend fun usernameTaken\([^)]*\)[^{]*\{(.*?)\n    \}", src, re.DOTALL)
        if ut_match:
            body = ut_match.group(1)
            if 'from("profiles")' in body:
                violations.append("Sangam.kt: usernameTaken() still queries profiles table directly (pre-login access)")
            elif '"username_available"' in body:
                checked.append("Android pre-login check: Sangam.usernameTaken() uses RPC 'username_available' (OK)")

    for dirpath, _, filenames in os.walk(kt_root):
        for fn in sorted(filenames):
            if not fn.endswith(".kt"):
                continue
            fpath = os.path.join(dirpath, fn)
            rel = os.path.relpath(fpath, main_root)
            with open(fpath, "r", encoding="utf-8") as f:
                lines = f.readlines()
            for lineno, line in enumerate(lines, 1):
                if 'from("profiles").select' in line:
                    m = re.search(r'from\("profiles"\)\.select\(([^)]*)\)', line)
                    arg = m.group(1).strip() if m else ""
                    if not arg or "Columns.ALL" in arg or '"*"' in arg:
                        violations.append(f"{rel}:{lineno}: uses unrestricted select({arg}) on profiles")
                    elif arg in col_constants:
                        cols = col_constants[arg]
                        bad = [c for c in cols if c not in GRANTED_COLUMNS]
                        if bad:
                            violations.append(f"{rel}:{lineno}: {arg} includes disallowed columns {bad}")
                        else:
                            checked.append(f"{rel}:{lineno}: select({arg}) -> {cols} (OK)")
                    elif "Columns.list" in arg:
                        cols = re.findall(r'"([a-zA-Z0-9_]+)"', arg)
                        bad = [c for c in cols if c not in GRANTED_COLUMNS]
                        if bad:
                            violations.append(f"{rel}:{lineno}: Columns.list includes disallowed columns {bad}")
                        else:
                            checked.append(f"{rel}:{lineno}: select(Columns.list({cols})) (OK)")
                    else:
                        violations.append(f"{rel}:{lineno}: unrecognized profiles select argument: {arg}")
    return checked, violations


def scan_windows(app_js_path):
    violations = []
    checked = []
    if not os.path.isfile(app_js_path):
        return checked, violations

    with open(app_js_path, "r", encoding="utf-8") as f:
        src = f.read()
    lines = src.splitlines()

    # Check pre-login lookupEmailByUsername
    lu_match = re.search(r"async function lookupEmailByUsername\([^)]*\)\s*\{(.*?)\n    \}", src, re.DOTALL)
    if lu_match:
        body = lu_match.group(1)
        if "/rest/v1/profiles" in body:
            violations.append("windows/app/app.js: lookupEmailByUsername() queries /rest/v1/profiles directly before login")
        elif "/functions/v1/login-lookup" in body:
            checked.append("Windows pre-login check: lookupEmailByUsername() uses Edge Function '/functions/v1/login-lookup' (OK)")

    for lineno, line in enumerate(lines, 1):
        if "/rest/v1/profiles" in line:
            # Check if it is a GET/SELECT query (has select= or no method: PATCH/POST/DELETE on the same/next lines)
            window = "\n".join(lines[max(0, lineno - 1):min(len(lines), lineno + 3)])
            is_write = any(m in window for m in ['method: "PATCH"', 'method: "POST"', 'method: "DELETE"', "method: 'PATCH'", "method: 'POST'", "method: 'DELETE'"])
            if "select=" in line:
                m = re.search(r"select=([a-zA-Z0-9_,*]+)", line)
                sel = m.group(1) if m else ""
                if sel == "*" or not sel:
                    violations.append(f"windows/app/app.js:{lineno}: uses select=* on profiles")
                else:
                    cols = [c.strip() for c in sel.split(",") if c.strip()]
                    bad = [c for c in cols if c not in GRANTED_COLUMNS]
                    if bad:
                        violations.append(f"windows/app/app.js:{lineno}: selects disallowed columns {bad} from profiles")
                    else:
                        checked.append(f"windows/app/app.js:{lineno}: select={','.join(cols)} (OK)")
            elif not is_write:
                violations.append(f"windows/app/app.js:{lineno}: GET /rest/v1/profiles without explicit select= list")

    return checked, violations


def main():
    print("=== PROFILE COLUMNS & PRE-LOGIN ACCESS SCAN ===")
    print("Granted columns:", ", ".join(sorted(GRANTED_COLUMNS)))
    kt_checked, kt_violations = scan_kotlin(MAIN_ROOT)
    win_checked, win_violations = scan_windows(WIN_APP_JS)

    for item in kt_checked + win_checked:
        print("  [PASS]", item)

    all_violations = kt_violations + win_violations
    if all_violations:
        for v in all_violations:
            print("  [FAIL]", v)
        sys.exit(1)

    print(f"\nSUMMARY: {len(kt_checked) + len(win_checked)} checks passed, 0 violations found.")


if __name__ == "__main__":
    main()
