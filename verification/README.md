# Isolated compatibility probe

Never install this probe on a live server: it shuts down its disposable test server.
Compile `TradeSystemCompatibilityProbe.java` with JDK 25 against the test server's
API/library classpath and package it with this directory's `plugin.yml`.

Use fresh, whitelisted servers bound to localhost, with the release TradeSystem JAR
and this probe as the only plugins. Test both Minecraft 26.2 and 26.3. The probe
writes `compatibility-result.txt` and shuts down after checking:

- TradeSystem enabled and passed its own complete NMS initialization check.
- The shaded CodingAPI identifies the actual server version.
- Temporary sign content packets can be constructed with the appropriate
  component-array or component-list and front-side APIs.
- Incoming sign text can be read through CodingAPI's resolved field accessor.
- CodingAPI's actual editor-opening method constructs and dispatches its packet
  on a server with zero connected players.

All sign text is synthetic; no player is connected and no real block is placed.
This does not verify client rendering, two-player trades, or economy integration.

Validated on 2026-10-07 against isolated CannonLeaf 26.2 and Leaf 26.3 copies.
The prior upstream JAR failed startup on Leaf 26.3; the shared Impeerium artifact
passed this probe on both versions. Maven `clean verify` also passed; the upstream
reactor has no unit tests. Live activation and client trading remain separate checks.
