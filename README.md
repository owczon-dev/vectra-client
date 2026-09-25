# Vectra Client

A client-side Fabric utility mod for **Minecraft 26.2**.

Everything lives behind a ClickGUI opened with **P**.

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

**Combat**

| Module | What it does |
|---|---|
| TBot | Attacks (and swings at) the nearest other player that comes within range |
| Shield Breaker | Swaps to an axe for one hit when you attack a blocking player, then swaps back |

**TBot** has three settings: **Range** (1–6 blocks, default 3), **Attack Delay** (0–40 ticks
between attacks, default 10) and **Require Crosshair** (off by default — turn it on to only attack
when the player in range is also the one you're aiming at). It attacks through
`MultiPlayerGameMode#attack`, the same path vanilla's own left-click takes, so the hit lands rather
than only animating.

**Shield Breaker** only reacts to *your* attack — it never attacks on its own. While you hold attack
with a blocking player under your crosshair, it swaps the axe from your hotbar into your hand, hits,
then returns the previously held item. An axe hit disables a shield for a few seconds; a sword swing
just bounces off. Settings: **Range** (1–6, default 4), **Swap Back** (1–20 ticks it holds the axe,
default 4) and **Cooldown** (0–40 ticks between swaps, default 10). If you're already holding an axe
it does nothing and lets the vanilla hit do the work.

Both modules are client-side. Shield Breaker keeps the server in sync by sending the carried-item
packet itself, because `MultiPlayerGameMode` only re-sends it when it next attacks or interacts —
without that the server would keep thinking you were holding the axe after the swap back.

**Movement**, **World**, **Utility** and **Uncategorized** are listed in the GUI but have no modules
yet.

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
│   └── impl/               FpsDisplay, PingDisplay, ReachDisplay, TBot
├── hud/HudRenderer         draws the enabled display modules via HudElementRegistry
├── gui/ClickGuiScreen      the ClickGUI panel
├── gui/widget/SettingSlider  slider bound to a DoubleSetting
├── util/TargetUtil         ray-march used to find the entity under the crosshair
└── config/ConfigManager    loads/saves module state + settings as JSON
```
