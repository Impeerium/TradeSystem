# TradeSystem on Impeerium

Verified 2026-11-09. Read the active administrator agreement for the current host or developer container.

- Maintained source: `https://github.com/Impeerium/TradeSystem`, default branch `develop`.
  Checkout: `/home/artur-admin/impeerium-dev/TradeSystem`.
  Upstream: `https://github.com/erikzimmermann/TradeSystem`, branch `develop`.
- Build from the repository root with JDK 25 and Maven 3.9.x: `mvn -B -ntp clean verify`.
  The optional ImpeeriumAPI 1.11 and current Paper compile artifacts have Java 25
  classfiles; the player component adapter requires that compiler. Packets retain
  their Java 8 bytecode target and existing wire protocol. One shared build serves both current 26.x targets.
  `-DimpeeriumApiRepository=file:///absolute/path/to/repository` selects the verified
  API release repository when Packages is unavailable; default is the local Maven repo.
  There is no Maven wrapper. Verified developer-container tools are under
  `/home/istomol/.cache/impeerium-mechanics-tools/{jdk-25.0.4.1+1,apache-maven-3.9.16}`.
  The earlier host JDK21 toolchain is sufficient only for the unmodified baseline;
  locate a current JDK25 before building this API consumer on that host.
  EngineHub serves an empty POM for `worldguard-bukkit:7.0.12` (its dependencies are only in
  Gradle module metadata), so a fresh Maven repository fails on `WorldGuardAdapter`; build with a
  local repository that also has `worldguard-core:7.0.12` and `worldedit-core`/`-bukkit:7.3.0`.
- Deploy only the shaded `TradeSystem-Bundle/target/TradeSystem_v${version}.jar`,
  never `original-*`, `TradeSystem-Spigot` or `TradeSystem-Packets` artifacts.
  Plugin identity is `TradeSystem`, main class `de.codingair.tradesystem.spigot.TradeSystem`.
- CodingAPI is pinned to upstream PR 37's full commit, with JitPack coordinates
  `com.github.rudde0:CodingAPI:9a7230cd80502f6e6a092370370e5095cca49851`.
  It supports 26.3's sign packet changes while retaining the 26.2 code path.
  Keep the dependency identical in the Spigot and Bundle modules; do not bundle
  a second unpatched CodingAPI transitively.
- All installed targets use `plugins/TradeSystem_v2.6.3_Hotfix-9.jar` beneath
  `/var/lib/pterodactyl/volumes/`:
  Factions `f25b6037-6e38-4d08-8494-0bf41467e9e5` (CannonLeaf 26.2),
  Survival `c8890de8-a5c2-49a3-93fb-d315300c2816` (Canvas 26.3, Folia, since 2026-11-09),
  Events `70d01883-9711-4c0c-97a1-b817dd2408ef` (Canvas 26.3).
  Preserve owner/group 988:988, back up each JAR, atomically replace it, and
  compare every SHA-256 value with the release artifact. Ship one identical JAR.
- Folia (Canvas): the global region owns no player. Never use UniversalScheduler's plain
  `runTask`/`runTaskLater`/`runTaskTimer` for work that touches a player's menu, inventory or
  drops; use `trade/TradeThreads` (the trader's own scheduler) and keep the Paper branch unchanged.
  Trade-wide work (countdown, `updateLater`) runs on the initiating trader's thread and checks
  `Trade#colocated()`; a trade whose traders end up on different region threads is cancelled
  half by half on each trader's own thread. `FoliaTradeThreadTest` covers this.
- Existing `plugins/TradeSystem` configs, layouts, translations and databases
  remain local to each server. They must not enter this repository.
- JAR activation requires a server restart. Installing a JAR is not activation;
  use the host agreement's authorization and countdown procedure. Avoid hot reload.
- Player ET/EN presentation uses optional ImpeeriumAPI1.11, retaining native files
  when the API is absent/unavailable. Shared pack prerequisites are the additive
  `impeerium:tradesystem_frames` and `impeerium:gui_label_p27` fonts with the neutral
  trade frame; publish them before activating this consumer. Keep the old F813/F810
  glyphs and each server's configured title/lock offsets. Client acceptance remains
  a separate check from the unit suite and offline title previews.
- `verification/` contains a disposable-server compatibility probe. Never deploy
  that probe to a real server: it intentionally shuts its test server down.
  Build the plugin and compile/run the probe and 26.x servers with JDK 25.
