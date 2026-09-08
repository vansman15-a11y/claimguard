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

- Place a **Claim Core** → protects a full-height column centered on itself:
  `radius` blocks out on X and Z (starts 8, i.e. 17×17), from bedrock to the
  build limit.
- Other players can't break/place blocks, open containers, or interact with
  anything inside the cube. Server operators always bypass this.
- Right-click your own core holding one of the upgrade items (the full cost is
  taken from anywhere in your inventory):
  - Level 1→2: 8 diamonds, radius 8→16
  - Level 2→3: 16 diamonds, radius 16→24
  - Level 3→4: 32 diamonds, radius 24→32  *(good "starter base" size, 65×65 footprint)*
  - Level 4→5: 48 diamonds + 1 blaze rod, radius 32→42
  - Level 5→6 (max): 64 diamonds + 2 blaze rods, radius 42→52  *(105×105 footprint)*
- Right-click your own core **bare-handed** to flash the claim border in the
  world for 30s; right-click again to hide it.
- Only the owner can break the core (which deletes the claim).
- Claims persist through server restarts.

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
