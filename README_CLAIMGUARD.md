# ClaimGuard — ready-to-open project

This is your official Forge 1.20.1 (build 47.4.23) MDK with the example mod removed
and ClaimGuard's source already merged in. No manual copying needed.

## 1. Open it

Install IntelliJ IDEA (Community edition is fine) if you don't have it. Open this
whole folder as a project — IntelliJ will detect `build.gradle` and offer to import
it as a Gradle project. Accept, and let it download dependencies (first time takes
a while; it's fetching Minecraft's own libraries and setting up mappings).

## 2. Run it

In IntelliJ's Gradle side panel, find and run **runClient** under
Tasks > forgegradleruns (or similar path). This launches a real Minecraft client
with ClaimGuard loaded. First launch takes a few minutes.

Once it's up: Creative mode → find the **ClaimGuard** tab → grab a **Claim Core** →
place it and test placing/breaking/right-clicking as another player would (you can
test the "not your claim" behavior using two accounts, or just trust the code review
for that part until you have a friend test it).

To exercise the server-side protection logic specifically, run **runServer** instead,
or just test in a normal singleplayer world (Forge singleplayer runs an internal
server under the hood).

## 3. Build the real mod jar for your actual server

Run the **build** Gradle task (or open a terminal in this folder and run
`./gradlew build` on Mac/Linux or `gradlew.bat build` on Windows).
The finished jar lands in `build/libs/claimguard-0.1.0.jar` — copy that one file
into your server's `mods` folder via AMP's File Manager, same as you did for
Distant Horizons.

## What it does right now

- Craft a **Claim Core** — vanilla beacon layout (5 glass, 3 obsidian) with a
  **diamond block** where the nether star goes. You must be **in a clan** for the
  recipe to produce anything.
- Place it → protects a full-height column centered on itself: `radius` blocks
  out on X and Z (starts 8, i.e. 17×17), from bedrock to the build limit.
- Other players can't break/place blocks, open containers, or interact with
  anything inside the cube. Server operators always bypass this.
- Right-click your own core → **Clan Beacon menu**:
  - **Show border (30s)** – outlines the claim in the world; right-click the
    core again (or reopen) to re-trigger.
  - **Bind / Leave bind** – makes this your respawn point (bindstone). See below.
  - **Upgrade** → a table of every tier, its item cost, and the size it grants,
    with the next step highlighted. *Confirm Upgrade* spends the items (taken
    from anywhere in your inventory) and refreshes the table.
  - **Remove** → a confirmation prompt; confirming breaks the core (it drops so
    you can re-place it) and unprotects the area.
  - Tiers: L1→2 8 diamond (r8→16); L2→3 16 diamond (r16→24); L3→4 32 diamond
    (r24→32, "starter base"); L4→5 48 diamond + 1 blaze rod (r32→42);
    L5→6 64 diamond + 2 blaze rods (r42→52, max).
- Only the owner can break the core (which deletes the claim).
- Claims persist through server restarts.

## Clans

- Craft a **Clan Charter** (3 paper + ink sac + any banner). Right-click it to
  become its owner (adds your own signature).
- `/signature <player>` asks someone to sign. They reply `/accept`, `/deny`, or
  `/block` (block = they never get a request from you again).
- With enough signatures (**1 while testing**, 3 for real), right-click the
  charter → **Create Clan** (name / tag / motto). Co-signers join as Members.
  Clans hold up to 8.
- `/clan` opens the roster: All / Online filter, one row per member. Right-click
  a member for **Whisper** plus **Promote / Demote / Kick / Ban** (only the ones
  your rank allows).
  - Leader: everything. Officer: promote / demote / kick players below them (never
    to their own rank, never ban). Member / Recruit: whisper + invite only.
- Roster buttons: **Invite** (`/clan invite <player>` → they `/clan accept`),
  **MOTD** (leader/officer can edit), **Ban list** (leader can unban).
- The **"Territory of …"** message shows the owning clan's name (whatever you
  named your clan). Clan membership doesn't affect claim *protection* yet — still
  owner-only.

## Bindstone

- In the beacon menu, **Bind** makes that claim core your respawn point.
- If you're bound and die **with no bed**, you revive at the bindstone.
- If you're bound **and** have a bed, death shows a **choice screen**: revive at
  the bindstone or at your bed.
- **Leave bind** clears it — you go back to the normal bed / world-spawn system.

## Known placeholders

- **Look**: renders as a vanilla beacon (glass shell + glowing core) with a full-height
  white beam shooting up, drawn by `client/ClaimCoreRenderer.java`. No custom art yet -
  swap `models/block/claim_core.json` and/or the beam colour in `ClaimCoreRenderer`
  for something matching your altar screenshot whenever you're ready.
- **No trusted-player list yet** — owner-only for now.
- **Clan ownership / bed-style respawn**: noted with comments in `Claim.java` as the
  planned next steps, not implemented yet.

If the build fails or you hit a red-underlined error in IntelliJ, paste the exact
message back and I'll fix the source.
