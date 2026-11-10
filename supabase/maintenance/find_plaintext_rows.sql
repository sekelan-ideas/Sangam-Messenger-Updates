-- ============================================================================
-- FIND PLAINTEXT / COMPANION-SUFFIX MESSAGE ROWS
-- Run this in the Supabase SQL Editor to inspect any message row that still
-- contains '|w:' or starts with '9:' so you can decide to delete it.
-- ============================================================================

select id,
       created_at,
       left(ciphertext, 12) as head,
       (position('|w:' in ciphertext) > 0) as has_w_suffix,
       (ciphertext like '9:%') as starts_with_9
  from public.messages
 where ciphertext like '%|w:%'
    or ciphertext like '9:%'
 order by created_at desc;

-- To delete a specific old row after inspecting its id above, run:
-- delete from public.messages where id = 'PASTE-ROW-UUID-HERE';
