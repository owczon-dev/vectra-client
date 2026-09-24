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
| TBot | Swings your main hand whenever another player comes within range |

TBot has three settings: **Range** (1–6 blocks, default 3), **Swing Delay** (0–40 ticks between
swings, default 10) and **Require Crosshair** (off by default — turn it on to only swing when the
player in range is also the one you're aiming at).

Per the spec, TBot only performs the *swing animation* — it sends no attack packet and deals no
damage. To make it hit as well, add `client.gameMode.attack(player, target);` next to the
`player.swing(...)` call in `TBotModule#onTick`.

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
Fabric API.

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
