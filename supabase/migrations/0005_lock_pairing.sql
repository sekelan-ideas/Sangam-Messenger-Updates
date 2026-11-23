-- ============================================================================
-- Migration: 0005_lock_pairing.sql
-- Purpose  : Wipe plaintext/base64 credentials from public.desktop_pairings
--            and revoke all client access (anon, authenticated) so pairing
--            secrets can never be written to or read from the database table.
--
-- Risk     : LOW. Modern QR pairing uses AES-256-GCM encrypted blobs
--            (pair_<code>.bin) in Storage with an ephemeral key passed only
--            inside the QR code. Old app versions (v2.14.1 / v1.0) that relied
--            on public.desktop_pairings must be updated to v2.14.2+.
--
-- Verify   :
--   1. SELECT count(*) FROM public.desktop_pairings;           -- expected: 0
--   2. SELECT grantee, privilege_type
--        FROM information_schema.role_table_grants
--       WHERE table_schema = 'public'
--         AND table_name = 'desktop_pairings'
--         AND grantee IN ('anon', 'authenticated', 'PUBLIC');  -- expected: 0 rows
--
-- Rollback :
--   GRANT SELECT, INSERT, UPDATE, DELETE ON public.desktop_pairings TO authenticated;
-- ============================================================================

BEGIN;

-- 1. Wipe any existing or leftover pairing rows containing tokens or passwords
DELETE FROM public.desktop_pairings;

-- 2. Revoke all access from public roles (keep table for admin inspection only)
REVOKE ALL ON TABLE public.desktop_pairings FROM PUBLIC;
REVOKE ALL ON TABLE public.desktop_pairings FROM anon;
REVOKE ALL ON TABLE public.desktop_pairings FROM authenticated;

-- 3. Drop any permissive RLS policies on desktop_pairings while keeping RLS enabled
ALTER TABLE public.desktop_pairings ENABLE ROW LEVEL SECURITY;

DO $$
DECLARE
    pol record;
BEGIN
    FOR pol IN
        SELECT policyname
          FROM pg_policies
         WHERE schemaname = 'public'
           AND tablename = 'desktop_pairings'
    LOOP
        EXECUTE format('DROP POLICY IF EXISTS %I ON public.desktop_pairings', pol.policyname);
    END LOOP;
END $$;

COMMIT;
