# TradeSystem on Impeerium

Verified 2026-10-07. Read `/home/pterodactyl/network-ops/AGENTS.md` for the host agreement.

- Maintained source: `https://github.com/Impeerium/TradeSystem`, default branch `develop`.
  Checkout: `/home/artur-admin/impeerium-dev/TradeSystem`.
  Upstream: `https://github.com/erikzimmermann/TradeSystem`, branch `develop`.
- Build from the repository root with JDK 21 and Maven 3.9.9: `mvn -B -ntp clean verify`.
  There is no Maven wrapper. Host tools are under
  `/home/pterodactyl/impeerium-build/toolchain/{jdk-21.0.11+10,apache-maven-3.9.9}`.
- Deploy only the shaded `TradeSystem-Bundle/target/TradeSystem_v${version}.jar`,
  never `original-*`, `TradeSystem-Spigot` or `TradeSystem-Packets` artifacts.
  Plugin identity is `TradeSystem`, main class `de.codingair.tradesystem.spigot.TradeSystem`.
- CodingAPI is pinned to upstream PR 37's full commit, with JitPack coordinates
  `com.github.rudde0:CodingAPI:9a7230cd80502f6e6a092370370e5095cca49851`.
  It supports 26.3's sign packet changes while retaining the 26.2 code path.
  Keep the dependency identical in the Spigot and Bundle modules; do not bundle
  a second unpatched CodingAPI transitively.
- Both installed targets use `plugins/TradeSystem_v2.6.3_Hotfix-9.jar` beneath
  `/var/lib/pterodactyl/volumes/`:
  Factions `f25b6037-6e38-4d08-8494-0bf41467e9e5` (CannonLeaf 26.2),
  Survival `c8890de8-a5c2-49a3-93fb-d315300c2816` (Leaf 26.3).
  Preserve owner/group 988:988, back up each JAR, atomically replace it, and
  compare both SHA-256 values with the release artifact. Ship one identical JAR.
- Existing `plugins/TradeSystem` configs, layouts, translations and databases
  remain local to each server. They must not enter this repository.
- JAR activation requires a server restart. Installing a JAR is not activation;
  use the host agreement's authorization and countdown procedure. Avoid hot reload.
- `verification/` contains a disposable-server compatibility probe. Never deploy
  that probe to a real server: it intentionally shuts its test server down.
  Build the plugin with JDK 21; compile/run the probe and 26.x servers with JDK 25.
