// Sangam account deletion (Supabase Edge Function).
//
// One-time setup:
//   Supabase -> Edge Functions -> delete-account -> Code -> paste this file -> Deploy.
//   No custom secrets needed; SUPABASE_URL, SUPABASE_ANON_KEY, and SUPABASE_SERVICE_ROLE_KEY
//   are provided automatically by Supabase. Keep JWT verification ON.
//
// What happens on account deletion:
//   1. Checks the error returned by EVERY database delete/update and storage remove.
//      Only records a table or bucket in `clearedTables` / `clearedBuckets` if the
//      operation returned no error; any error is recorded in `failures`.
//   2. Groups (`chat_groups` & `group_members`):
//      - If the user created a group (`created_by = uid`), the function checks remaining
//        members (`user_id <> uid`).
//      - If other members remain, leadership (`chat_groups.created_by` and
//        `group_members.is_admin = true`) is transferred to the oldest co-leader
//        (`is_admin = true`), or else the oldest remaining member, and only the departing
//        user's membership row is deleted.
//      - Only if the group has zero remaining members is the group itself deleted.
//   3. Messages (`public.messages`):
//      - Deletes all rows where `sender_id = uid` OR `recipient_id = uid`. Both messages
//        the deleted user sent to others and messages others sent to the deleted user are
//        deleted from the server database (`public.messages`). Copies already decrypted
//        and cached locally on another user's device remain on that device only. Messages
//        between other members in surviving groups (`sender_id <> uid` and
//        `recipient_id <> uid`) are preserved.
//   4. Safety guard:
//      - If deleting the `public.profiles` row fails, the function does NOT delete the
//        `auth.users` account and returns HTTP 500 with the `failures` list so the user
//        is not left with an orphaned profile.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

export interface DeleteFailure {
  step: string;
  error: string;
}

export async function handleDeleteAccount(
  _req: Request,
  supa: any,
  admin: any,
): Promise<Response> {
  const jsonResponse = (obj: unknown, status = 200) =>
    new Response(JSON.stringify(obj), {
      status,
      headers: { "Content-Type": "application/json" },
    });

  const { data: authData, error: authErr } = await supa.auth.getUser();
  const user = authData?.user;
  if (authErr || !user) {
    return jsonResponse({ error: "unauthorized" }, 401);
  }

  const uid = user.id;
  const userEmail = user.email ?? null;
  const clearedTables: string[] = [];
  const clearedBuckets: Record<string, string[]> = {
    avatars: [],
    "message-images": [],
  };
  const transferredGroups: Array<{ group_id: string; new_leader: string }> = [];
  const deletedEmptyGroups: string[] = [];
  const failures: DeleteFailure[] = [];

  const recordResult = (step: string, err: any, onOk?: () => void) => {
    if (err) {
      failures.push({ step, error: String(err.message ?? err) });
      return false;
    }
    if (onOk) onOk();
    return true;
  };

  // 1. Storage bucket: avatars
  try {
    const fixedAvatarPaths = [
      `${uid}.jpg`,
      `${uid}.png`,
      `outbox_${uid}.bin`,
      `outbox_${uid}.json`,
      `devs_${uid}.json`,
      `typing_${uid}.json`,
    ];
    const { data: avatarList, error: listErr } = await admin.storage
      .from("avatars")
      .list("", { limit: 1000 });
    if (listErr) {
      failures.push({ step: "storage.avatars.list", error: String(listErr.message ?? listErr) });
    } else {
      const dynamicAvatars = Array.isArray(avatarList)
        ? avatarList
            .map((f: any) => f?.name as string)
            .filter(
              (name: string) =>
                Boolean(name) &&
                (name === `${uid}.jpg` ||
                  name === `${uid}.png` ||
                  name.startsWith(`u_${uid}_`) ||
                  name.startsWith(`outbox_${uid}`) ||
                  name.startsWith(`devs_${uid}`) ||
                  name.startsWith(`typing_${uid}`) ||
                  ownerMatches(avatarList, name, uid)),
            )
        : [];
      const toRemoveAvatars = Array.from(new Set([...fixedAvatarPaths, ...dynamicAvatars]));
      if (toRemoveAvatars.length > 0) {
        const { error: rmErr } = await admin.storage.from("avatars").remove(toRemoveAvatars);
        recordResult("storage.avatars.remove", rmErr, () => {
          clearedBuckets.avatars.push(...toRemoveAvatars);
        });
      }
    }
  } catch (e) {
    failures.push({ step: "storage.avatars", error: String(e) });
  }

  // 2. Storage bucket: message-images
  try {
    const prefixes = ["img", "audio", "docs", "video", "thumbs", "gphoto"];
    const toRemoveMedia: string[] = [];
    let listFailed = false;
    for (const prefix of prefixes) {
      const { data: files, error: listErr } = await admin.storage
        .from("message-images")
        .list(prefix, { limit: 1000 });
      if (listErr) {
        failures.push({
          step: `storage.message-images.list(${prefix})`,
          error: String(listErr.message ?? listErr),
        });
        listFailed = true;
      } else if (Array.isArray(files)) {
        for (const f of files) {
          if (f && (f.owner === uid || f.owner_id === uid)) {
            toRemoveMedia.push(`${prefix}/${f.name}`);
          }
        }
      }
    }
    if (toRemoveMedia.length > 0) {
      const { error: rmErr } = await admin.storage.from("message-images").remove(toRemoveMedia);
      recordResult("storage.message-images.remove", rmErr, () => {
        clearedBuckets["message-images"].push(...toRemoveMedia);
      });
    } else if (!listFailed) {
      // No owned files in message-images; bucket check succeeded
    }
  } catch (e) {
    failures.push({ step: "storage.message-images", error: String(e) });
  }

  // 3. Database tables
  // 3a) call_chunks & 3b) calls
  try {
    const { data: userCalls, error: callsSelErr } = await admin
      .from("calls")
      .select("id")
      .or(`caller.eq.${uid},callee.eq.${uid}`);
    if (callsSelErr) {
      failures.push({ step: "calls.select", error: String(callsSelErr.message ?? callsSelErr) });
    } else {
      let chunksOk = true;
      if (Array.isArray(userCalls) && userCalls.length > 0) {
        const callIds = userCalls.map((c: any) => c.id);
        const { error: ccErr1 } = await admin.from("call_chunks").delete().in("call_id", callIds);
        if (!recordResult("call_chunks.delete(by_call_id)", ccErr1)) chunksOk = false;
      }
      const { error: ccErr2 } = await admin.from("call_chunks").delete().eq("from_user", uid);
      if (!recordResult("call_chunks.delete(by_from_user)", ccErr2)) chunksOk = false;
      if (chunksOk) clearedTables.push("call_chunks");
    }

    const { error: callsDelErr } = await admin
      .from("calls")
      .delete()
      .or(`caller.eq.${uid},callee.eq.${uid}`);
    recordResult("calls.delete", callsDelErr, () => clearedTables.push("calls"));
  } catch (e) {
    failures.push({ step: "calls_and_chunks", error: String(e) });
  }

  // 3c) messages (deletes rows where sender_id = uid OR recipient_id = uid)
  try {
    const { error: msgErr } = await admin
      .from("messages")
      .delete()
      .or(`sender_id.eq.${uid},recipient_id.eq.${uid}`);
    recordResult("messages.delete", msgErr, () => clearedTables.push("messages"));
  } catch (e) {
    failures.push({ step: "messages.delete", error: String(e) });
  }

  // 3d) chat_groups & group_members (transfer leadership to oldest co-leader, else oldest member; delete group only if empty)
  try {
    let groupsOk = true;
    const { data: ownedGroups, error: grpSelErr } = await admin
      .from("chat_groups")
      .select("id")
      .eq("created_by", uid);

    if (grpSelErr) {
      groupsOk = false;
      failures.push({ step: "chat_groups.select", error: String(grpSelErr.message ?? grpSelErr) });
    } else if (Array.isArray(ownedGroups)) {
      for (const g of ownedGroups) {
        const gid = g.id;
        const { data: members, error: memSelErr } = await admin
          .from("group_members")
          .select("*")
          .eq("group_id", gid);
        if (memSelErr) {
          groupsOk = false;
          failures.push({
            step: `group_members.select(${gid})`,
            error: String(memSelErr.message ?? memSelErr),
          });
          continue;
        }
        const remaining = (Array.isArray(members) ? members : [])
          .filter((m: any) => m.user_id && m.user_id !== uid)
          .sort((a: any, b: any) => {
            const ta = Date.parse(a.joined_at || a.created_at || "1970-01-01T00:00:00Z") || 0;
            const tb = Date.parse(b.joined_at || b.created_at || "1970-01-01T00:00:00Z") || 0;
            if (ta !== tb) return ta - tb;
            return String(a.user_id).localeCompare(String(b.user_id));
          });

        if (remaining.length > 0) {
          // Prefer oldest co-leader (is_admin = true), else oldest member
          const coLeaders = remaining.filter((m: any) => m.is_admin === true);
          const successor = coLeaders.length > 0 ? coLeaders[0] : remaining[0];
          const newLeaderId = successor.user_id;

          const { error: upGrpErr } = await admin
            .from("chat_groups")
            .update({ created_by: newLeaderId })
            .eq("id", gid);
          if (!recordResult(`chat_groups.transfer_leader(${gid})`, upGrpErr)) {
            groupsOk = false;
            continue;
          }
          const { error: upMemErr } = await admin
            .from("group_members")
            .update({ is_admin: true })
            .eq("group_id", gid)
            .eq("user_id", newLeaderId);
          if (!recordResult(`group_members.promote_leader(${gid})`, upMemErr)) {
            groupsOk = false;
          }
          transferredGroups.push({ group_id: gid, new_leader: newLeaderId });
        } else {
          // Group is empty once this user leaves -> delete group messages, memberships, and group
          const { error: gmDelErr } = await admin.from("group_members").delete().eq("group_id", gid);
          if (!recordResult(`group_members.delete_empty(${gid})`, gmDelErr)) groupsOk = false;
          const { error: gMsgDelErr } = await admin.from("messages").delete().eq("group_id", gid);
          if (!recordResult(`messages.delete_empty_group(${gid})`, gMsgDelErr)) groupsOk = false;
          const { error: gDelErr } = await admin.from("chat_groups").delete().eq("id", gid);
          if (recordResult(`chat_groups.delete_empty(${gid})`, gDelErr)) {
            deletedEmptyGroups.push(gid);
          } else {
            groupsOk = false;
          }
        }
      }
    }

    const { error: myMemDelErr } = await admin.from("group_members").delete().eq("user_id", uid);
    if (recordResult("group_members.delete(self)", myMemDelErr)) {
      clearedTables.push("group_members");
    } else {
      groupsOk = false;
    }
    if (groupsOk) {
      clearedTables.push("chat_groups");
    }
  } catch (e) {
    failures.push({ step: "groups_cleanup", error: String(e) });
  }

  // 3e) devices
  try {
    const { error } = await admin.from("devices").delete().eq("user_id", uid);
    recordResult("devices.delete", error, () => clearedTables.push("devices"));
  } catch (e) {
    failures.push({ step: "devices.delete", error: String(e) });
  }

  // 3f) prekey_bundles
  try {
    const { error } = await admin.from("prekey_bundles").delete().eq("user_id", uid);
    recordResult("prekey_bundles.delete", error, () => clearedTables.push("prekey_bundles"));
  } catch (e) {
    failures.push({ step: "prekey_bundles.delete", error: String(e) });
  }

  // 3g) backups
  try {
    const { error } = await admin.from("backups").delete().eq("user_id", uid);
    recordResult("backups.delete", error, () => clearedTables.push("backups"));
  } catch (e) {
    failures.push({ step: "backups.delete", error: String(e) });
  }

  // 3h) abuse_reports
  try {
    const { error } = await admin
      .from("abuse_reports")
      .delete()
      .or(`reporter_id.eq.${uid},target_id.eq.${uid}`);
    recordResult("abuse_reports.delete", error, () => clearedTables.push("abuse_reports"));
  } catch (e) {
    failures.push({ step: "abuse_reports.delete", error: String(e) });
  }

  // 3i) desktop_pairings
  try {
    if (userEmail) {
      const { error } = await admin.from("desktop_pairings").delete().eq("email", userEmail);
      recordResult("desktop_pairings.delete", error, () => clearedTables.push("desktop_pairings"));
    } else {
      clearedTables.push("desktop_pairings");
    }
  } catch (e) {
    failures.push({ step: "desktop_pairings.delete", error: String(e) });
  }

  // 3j) profiles (MUST succeed before deleting auth.users)
  let profileDeleted = false;
  try {
    const { error: profErr } = await admin.from("profiles").delete().eq("id", uid);
    profileDeleted = recordResult("profiles.delete", profErr, () => clearedTables.push("profiles"));
  } catch (e) {
    failures.push({ step: "profiles.delete", error: String(e) });
  }

  if (!profileDeleted) {
    return jsonResponse(
      {
        done: false,
        error: "Profile deletion failed; auth user was not deleted.",
        clearedTables,
        clearedBuckets,
        transferredGroups,
        deletedEmptyGroups,
        failures,
      },
      500,
    );
  }

  // 4. Delete auth.users account only after profile delete succeeded
  const { error: authDelErr } = await admin.auth.admin.deleteUser(uid);
  if (!recordResult("auth.users.delete", authDelErr, () => clearedTables.push("auth.users"))) {
    return jsonResponse(
      {
        done: false,
        error: String(authDelErr?.message ?? authDelErr),
        clearedTables,
        clearedBuckets,
        transferredGroups,
        deletedEmptyGroups,
        failures,
      },
      500,
    );
  }

  return jsonResponse({
    done: failures.length === 0,
    clearedTables,
    clearedBuckets,
    transferredGroups,
    deletedEmptyGroups,
    failures,
  });
}

function ownerMatches(list: any[], name: string, uid: string): boolean {
  const item = list.find((x: any) => x && x.name === name);
  return Boolean(item && (item.owner === uid || item.owner_id === uid));
}

if (typeof Deno !== "undefined" && typeof Deno.serve === "function") {
  Deno.serve(async (req: Request) => {
    const authHeader = req.headers.get("Authorization") ?? "";
    const supa = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_ANON_KEY")!,
      { global: { headers: { Authorization: authHeader } } },
    );
    const admin = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
    return await handleDeleteAccount(req, supa, admin);
  });
}
