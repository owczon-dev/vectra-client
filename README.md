# Vectra Client

A client-side Fabric utility mod for **Minecraft 26.2**.

Everything lives behind a ClickGUI opened with **P**.

## Download (prebuilt)

The built mod jar is committed to this repo at:

```
release/vectra-client-0.1.0.jar
```

Download it with the **Download raw file** button on that file's GitHub page (or any raw-file
URL for it) and drop it into your `mods` folder. It is **44 053 bytes**;

```
sha256 341b8d42623702f55aca810b5fe5c3419353735a9809a7858ec8dc79d06670c9
```

Do **not** grab the jar out of a GitHub Actions run — those artifacts are served as a login-gated
zip, and saving that page as a `.jar` gives you a file that Fabric rejects with
`java.util.zip.ZipException: zip END header not found`. If you ever see that error, your copy is
truncated or is an HTML page: check that the size is 36 639 bytes before blaming the mod.

## Contents

### ClickGUI

Opened with **P** (rebindable in *Options → Controls → Vectra Client*). The panel has three
columns:

| Column | What it does |
|---|---|
| Categories | Render, Combat, Movement, World, Utility, Uncategorized |
| Modules | The modules in the selected category — click one to toggle it |
| Settings | Sliders / toggles for the currently selected module |

The panel is draggable by its title bar and remembers where you left it. The world keeps running
behind it (it doesn't pause the game), so combat modules stay live while you configure them.

### Modules

**Render**

| Module | What it does |
|---|---|
| FPS Display | Shows your current frame rate in the top-left of the HUD |
| Ping Display | Shows your latency to the server in milliseconds |
| Reach Display | Shows the distance to the entity currently under your crosshair |
| Overlay | Shows a coloured outline around other players and optionally their health |

**Combat**

| Module | What it does |
|---|---|
| TBot | Attacks (and swings at) the nearest other player that comes within range |
| Shield Breaker | Swaps to an axe for one hit when you attack a blocking player, then swaps back |

**Movement**

| Module | What it does |
|---|---|
| Jump Reset | Automatically jumps when you take damage |

**Utility**

| Module | What it does |
|---|---|
| Offhand | Swaps a totem of undying into your offhand when your health drops below a threshold |

**TBot** has five settings:

| Setting | Default | What it does |
|---|---|---|
| Range | 3 (1–6) | How close a player has to be before TBot hits them |
| Weapon Cooldown | on | Wait for the weapon's attack cooldown before hitting, so every hit lands at full damage |
| Min Charge (%) | 100 (0–100) | Fire once the cooldown reaches this much; lower it to trade damage for hit rate |
| Attack Delay (ticks) | 0 (0–40) | Extra ticks to wait *after* the cooldown is ready |
| Require Crosshair | off | Only attack when the player in range is also the one you're aiming at |

Weapon Cooldown is the important one. Attacking early still sends the packet, but vanilla scales
damage by `0.2 + charge² × 0.8`, so spraying hits at 30% charge does about a third of the damage —
that's why "spam clicking" feels useless. With it on, TBot times each hit to the moment your weapon
is charged (a netherite sword is ~12.5 ticks, an axe ~20), so it hits as fast as the weapon allows
and every hit counts. It reads the same cooldown the vanilla crosshair indicator uses, so it
adapts automatically to whatever you're holding and to Haste/Mining Fatigue-style attack-speed
effects.

It attacks through `MultiPlayerGameMode#attack`, the same path vanilla's own left-click takes, so
the hit lands rather than only animating.

**Shield Breaker** only reacts to *your* attack — it never attacks on its own. While you hold attack
with a blocking player under your crosshair, it swaps the axe from your hotbar into your hand, hits,
then returns the previously held item. An axe hit disables a shield for a few seconds; a sword swing
just bounces off. Settings: **Range** (1–6, default 4), **Swap Back** (1–20 ticks it holds the axe,
default 4) and **Cooldown** (0–40 ticks between swaps, default 10). If you're already holding an axe
it does nothing and lets the vanilla hit do the work.

Both modules are client-side. Shield Breaker keeps the server in sync by sending the carried-item
packet itself, because `MultiPlayerGameMode` only re-sends it when it next attacks or interacts —
without that the server would keep thinking you were holding the axe after the swap back.

**Jump Reset** detects the frame where your `hurtTime` transitions from 0 to positive (meaning
you were just hit) and calls `jumpFromGround()` if you're on the ground. That's the same method
vanilla's space-bar path uses — it directly applies the jump velocity. One-tick reaction time,
no input manipulation needed. No settings — it just works.

**Offhand** checks every tick whether your health is below the threshold and a totem of undying
is somewhere in your inventory. If so, it performs a `SWAP` container action on `containerId=0`
(the always-available player inventory menu) to move the totem into the offhand slot.

| Setting | Default | What it does |
|---|---|---|
| Health Threshold | 4 (1–20) | Swap when your health drops below this |
| Delay (ticks) | 2 (0–20) | Cooldown between swap attempts |
| Silent | off | Open the inventory screen briefly so the swap looks like a manual interaction |

The swap goes through `MultiPlayerGameMode#handleContainerInput`, the same code path that
vanilla's inventory screen uses for every click. The server sees a normal `SWAP` action on
`containerId=0`, which is always valid — no `OPEN_INVENTORY` handshake needed for auto mode.
In **auto** mode no GUI opens at all; in **silent** mode the inventory screen flashes open for
one tick (purely cosmetic — the swap packet is already sent).

**Overlay** draws a coloured outline around every other player and optionally their remaining
health above their head.

| Setting | Default | What it does |
|---|---|---|
| Outline | on | Coloured outline through walls (uses vanilla's built-in glow renderer) |
| Health | on | Floating health number above each player, colour-graded green → red |

The outline sets the vanilla glowing entity-data flag (bit 6 of the shared flags byte)
on every other player each tick and clears it on disable. Minecraft's own post-process
outline renderer handles the rest — no custom shaders, no framebuffer hacks. The health text
is submitted through the same `submitNameTag` code path that vanilla's own nametags use, so
it scales and fades the same way.

**World** and **Uncategorized** are listed in the GUI but have no modules yet.

### Config

Module states and settings are saved to `<config dir>/vectra-client.json` on every change and on
shutdown. A corrupt or hand-edited file is logged and ignored rather than crashing the game.

## Requirements

- **Java 25** (JDK) — the 26.2 client targets Java 25 bytecode
- **Fabric Loader** 0.19.5+
- **Fabric API** 0.161.0+26.2

## Building

```bash
./gradlew build
```

The jar lands in `build/libs/vectra-client-0.1.0.jar`. Drop it into your `mods/` folder alongside
Fabric API. Requires a **JDK 25** — Gradle itself needs JVM 17+, but `sourceCompatibility` needs 25
specifically.

Every push also builds on GitHub Actions and uploads the jar as the `vectra-client` artifact of that
run.

### Building without Gradle

Minecraft 26.1+ ships unobfuscated, so there is no remapping step — a mod jar is just compiled
classes plus processed resources. That means `javac` + `jar` are enough, and Gradle is only strictly
needed to *obtain* Minecraft, Fabric API and the loader (which it downloads from Maven and Mojang).

For machines that can't reach those hosts, `tools/` splits the two apart:

```bash
./tools/setup-toolchain.sh   # fetches a JDK 25 (javac/jar) + the compile classpath jars
./tools/build.sh             # compiles and packages release/vectra-client-<version>.jar
```

`setup-toolchain.sh` pulls both from the `ci-toolchain` branch, where GitHub Actions (which has
unrestricted network) publishes them. To regenerate that branch, push a commit whose message
contains `[toolchain]` — that triggers the `provision-toolchain` job in the workflow.

The resulting jar is equivalent to Gradle's: same entries, same `fabric.mod.json`.

## A note on the 26.2 API

Minecraft 26.1+ ships **unobfuscated**, which changed a lot of what mods are built on:

- No Yarn and no `mappings` dependency — Loom uses Mojang's names directly, via the
  **non-remapping** plugin id `net.fabricmc.fabric-loom` (not `fabric-loom-remap`).
- `MinecraftClient` → `Minecraft`, `KeyBinding` → `KeyMapping`, `GuiGraphics` → `GuiGraphicsExtractor`,
  and screens render through `extractRenderState(...)` instead of `render(...)`.
- Fabric's old `HudRenderCallback` is gone; HUD elements go through `HudElementRegistry`.
- `Minecraft` no longer owns the current screen directly — use `Minecraft#gui#screen()` /
  `setScreen(...)`.

## Project layout

```
dev.owczon.vectraclient
├── VectraClient            client entrypoint: keybinds, tick, HUD, config, shutdown
├── keybind/KeybindManager  registers the ClickGUI key and opens the screen on press
├── module/
│   ├── Module              base class: enable/disable lifecycle, tick dispatch, settings
│   ├── DisplayModule       a module that draws one line of text on the HUD
│   ├── ModuleCategory      Render / Combat / Movement / World / Utility / Uncategorized
│   ├── ModuleManager       owns every module instance and drives their ticks
│   ├── Setting             DoubleSetting / BooleanSetting
│   └── impl/               FpsDisplay, PingDisplay, ReachDisplay, TBot, ShieldBreaker,
│                             JumpReset, Offhand, Overlay
├── hud/HudRenderer         draws the enabled display modules via HudElementRegistry
├── gui/ClickGuiScreen      the ClickGUI panel
├── gui/widget/SettingSlider  slider bound to a DoubleSetting
├── util/TargetUtil         ray-march used to find the entity under the crosshair
└── config/ConfigManager    loads/saves module state + settings as JSON
```
