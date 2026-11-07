# PiCore

**Universal Core Framework & Shared Spine for BMS Plugins**

PiCore provides the unified architecture for all custom plugins across the server:
- **`PixelProfiler`**
- **`Koth`**
- **`MachineConstruct`**
- **`MorphicGames`**
- **`MorphicTrials`**
- **`PixelUtilities`**

---

### Features

1. **Declarative Universal Menu Engine**
   - `PiMenu`: Fluent builder with automated border fills, background masks, and PDC security stamping.
   - `PiButton`: Declarative click handling (left, right, shift), interactive audio feedback, and permission gating.
   - `PiPaginatedMenu`: Mathematical pagination engine with sticky navigation footers.

2. **3-Tier Defense-in-Depth Menu Security (`PiMenuGuard`)**
   - **Tier 1:** Instant cancellation at `EventPriority.LOWEST` for all `PiMenuHolder` inventories.
   - **Tier 2:** Persistent Data Container (`picore:gui`) item stamping with `HIGHEST` backstop cancellation.
   - **Tier 3:** Packet-lag sweeper cleans player inventories on close or join if any item desyncs.

3. **Item Provenance & Audit Components**
   - `TouchChainComponent`: Player-head timeline visualizing chains of custody, transfer handoffs (`INVSEE_TAKE`, `INVSEE_GIVE`, `CONTAINER_WITHDRAW`), and timestamps.
   - `SightingMapComponent`: Spatial container map tracking chests, barrels, shulkers, and ender chests with coordinate history.

4. **Public Ecosystem Health Ledger & Admin Alerts (`ecosystem.json`)**
   - Reads `https://raw.githubusercontent.com/scrunc/picore/main/ecosystem.json` without requiring private tokens.
   - Auto-discovers running BMS modules and alerts server administrators on join and via `/picore gui`.
   - **Zero auto-downloads for private/premium plugins**: Admins/buyers obtain updates via their customer portal.

5. **PiMorph — Arena Morph Engine** (`org.illustro.picore.morph`, toggle `modules.morph`)
   - Animates a region from one captured block/display state into another, swapping only the cells that differ.
   - 14 bundled choreographies: sweep, meteor, spread, rift, veins, spiral, rain, life, collapse, shatter, and the rise family (eruption, geyser, upheaval, springup).
   - Style packs are plain YAML in `plugins/PiCore/morphs/` — drop in a file to add a style.
   - Consumers: `getServer().getServicesManager().load(PiMorph.class)` (or `PiCore.getInstance().getMorph()`), then build a `MorphGrid` and call `morph(...)`.

6. **PiAudio — Voice Chat Audio Bridge** (`org.illustro.picore.audio`, toggle `modules.audio`)
   - Locational and per-player (static) audio streams through [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), with speech- or music-tuned encoding and live distance updates.
   - Holds the voice chat connection inside PiCore, so consumer plugins can be reloaded without losing audio.
   - `AudioTrack` decodes WAV and MP3 into 48 kHz mono frames; `AudioRing` is a PCM ring buffer for live sources.
   - `MediaTools` locates a shared `ffmpeg` / `yt-dlp` in `plugins/PiCore/bin/`, so one copy serves every plugin.
   - Simple Voice Chat is optional: without it the service stays registered and consumers simply play silent.

---

### Commands & Permissions

- `/picore status` (`picore.admin`) - Console and chat compatibility health report.
- `/picore check` (`picore.admin`) - Triggers an asynchronous re-fetch of `ecosystem.json`.
- `/picore gui` (`picore.admin`) - Interactive chest GUI showing module versions, release notes, and download notices.
- `/picore update` (`picore.admin`) - Stages PiCore core framework updates to `/plugins/update/PiCore.jar`.

---

### Building

```bash
mvn clean package
```
Generates `PiCore.jar`.
