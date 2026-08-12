# PrivacyCoords

Randomised coordinates for Minecraft servers. A player with PrivacyCoords enabled sees their whole
world shifted by a random amount, so pressing **F3** on stream shows coordinates that have nothing
to do with where their base actually is.

Nothing about the server changes. The shift lives entirely on the network layer: every position the
server sends to that one player is moved by `+offset`, and every position that player sends back is
moved by `-offset`. Blocks, entities, sounds, particles, the world border and the compass all move
together, so the world looks and behaves exactly as it always did - it just sits somewhere else on
the map as far as that client is concerned.

```
     what the server works with          offset          what F3 shows the player
        X:   214       Y: 71        →   X: +7808    →      X:  8022      Y: 71
        Z: -1893                        Z: -4192           Z: -6085
```

Other players are unaffected: each player gets their own offset, and players without the feature
enabled see the real coordinates.

## Commands

| Command | Permission | What it does |
| --- | --- | --- |
| `/privacycoords` | `privacycoords.use` | Shows your current status and offset |
| `/privacycoords enable` | `privacycoords.use` | Turns randomised coordinates on |
| `/privacycoords disable` | `privacycoords.use` | Turns them off again |
| `/privacycoords reroll` | `privacycoords.use` | Rolls a new random offset |
| `/privacycoords <sub> <player>` | `privacycoords.others` | Does the same for someone else |
| `/privacycoords reload` | `privacycoords.reload` | Re-reads `config.yml` |

Aliases: `/pcoords`, `/privacycoordinates`.

## When a change takes effect

**On the player's next login.** This is a protocol limitation rather than a shortcut: by the time a
player runs the command their client is already holding a world full of chunks at the old
coordinates, and the server will not resend chunks it believes the client already has. Flipping the
offset mid-session would leave the client stranded in an empty world.

If you would rather have it applied immediately, set `apply.kick-on-change: true` in the config.
The player is then kicked with a "reconnect to apply" message the moment they change the setting.

## Configuration

Everything the plugin does is configurable, and `config.yml` documents each option inline. All of
it is picked up by `/privacycoords reload` except `persistence.file`, which needs a restart.

| Key | What it controls |
| --- | --- |
| `enabled-by-default` | Whether new players get randomised coordinates without asking |
| `offset.min` / `offset.max` | The range the shift is drawn from, in blocks (default `-10000`..`10000`) |
| `offset.minimum-distance` | Refuses to roll anything smaller than this, so a roll cannot land on a useless 32 blocks |
| `offset.alignment` | Rounds rolled offsets to a multiple of this (default 16 = one chunk; 512 lines up with region files) |
| `offset.x.*` / `offset.z.*` | Per axis overrides of the three keys above |
| `persistence.enabled` | Remember each player's offset so their coordinates look the same every session |
| `persistence.reroll-on-join` | Roll a fresh offset on every login instead |
| `persistence.file` | Where offsets are stored, relative to the plugin folder |
| `apply.kick-on-change` | Kick on toggle so the change applies immediately - see above |
| `apply.kick-message` | What the kick screen says |
| `apply.notify-on-join` | Remind players on join that their coordinates are randomised |
| `apply.notify-delay-ticks` | How long to wait before sending that reminder |
| `permissions.use` / `.others` / `.reload` | Rename the permission nodes, or set one to `""` to give it to everybody |
| `translate.*` | Which categories of packet get shifted - see below |
| `advanced.chunk-data-fast-path` | Patch chunk coordinates in the packet buffer instead of decoding the column |
| `advanced.clientbound-priority` / `.serverbound-priority` | Where the two listeners sit relative to other packet plugins |
| `advanced.packetevents-update-checker` | Let the bundled PacketEvents check for its own updates |
| `debug` | Log every assigned offset and any packet that could not be translated |
| `messages.*` | Every player facing string |

The `translate` section has a switch for each category the plugin touches: `chunks`, `blocks`,
`player`, `spawn-position`, `last-death-position`, `entities`, `entity-metadata`, `effects`,
`particles`, `sounds`, `explosions`, `world-border`, `locator-bar`, and the two incoming ones,
`client-movement` and `client-block-interaction`. They are all on by default and should stay that
way - the offset is one consistent shift of the player's whole world, so switching a category off
leaves that part at its real coordinates while everything around it has moved. They exist so the
plugin can be narrowed down if it ever fights with another packet level plugin, and the console
warns on startup if a structural one is off.

Offsets are always rounded to at least a multiple of 16. A shift that is not chunk aligned cannot
be expressed in the packets that carry chunk coordinates, and would tear the client's world apart
at every chunk border, so smaller values are rejected and non-multiples are rounded up.

The Y axis is deliberately never shifted and there is no option to change that: the client is told
the world's height range once, and moving chunk sections outside of it breaks lighting and
rendering. A base is given away by X and Z, not by Y.

## Installing

1. Drop `PrivacyCoords-<version>.jar` into `plugins/`.
2. Start the server. That's it - PacketEvents is bundled inside the jar, relocated so it cannot
   clash with other plugins that ship their own copy.

Requires Java 17+. Runs on **Spigot, Paper, Folia and Folia forks such as CanvasMC** from the same
jar. PacketEvents supports 1.8 through current releases; PrivacyCoords is developed against modern
versions and is most thoroughly exercised there.

### Folia and CanvasMC

The plugin declares `folia-supported: true`, so Folia and its forks will load it.

Almost none of PrivacyCoords runs on a server thread in the first place, which is what makes it a
comfortable fit for regionised multithreading: the two packet listeners run on Netty threads, they
never touch a world, a chunk or an entity through the Bukkit API, and all the per-player state they
share lives in concurrent maps behind a single volatile config reference. There is nothing for a
region to own.

The little that does need a thread goes through a `PlatformScheduler` that is picked at startup:

| Work | Single threaded server | Regionised server |
| --- | --- | --- |
| Saving `data.yml` | async Bukkit task | `AsyncScheduler` |
| Kicking on a setting change | main thread task | the player's `EntityScheduler` |
| Messaging a player someone else changed | direct | the player's `EntityScheduler` |
| The join reminder | delayed main thread task | the player's `EntityScheduler`, dropped if they leave |

Which one is chosen depends on whether the regionised scheduler API exists, not on whether the
server calls itself Folia - Paper has shipped that API since 1.20 and implements it correctly on a
single main thread, and every Folia fork inherits it. So a fork this code has never heard of still
gets the right behaviour, and Spigot falls back to the classic scheduler. The startup log says
which one it picked.

## Building

```
mvn clean package
```

The shaded jar lands in `target/PrivacyCoords-<version>.jar`.

Every push and pull request also builds through the `Build` GitHub Actions workflow, which
compiles the plugin, checks that the jar contains the plugin classes, `plugin.yml` (ours, declaring
`folia-supported`), `config.yml` and a relocated copy of PacketEvents, and uploads the jar as a
build artifact.

Pushing a `v*` tag runs the `Release` workflow, which builds the same jar and publishes it as a
GitHub release.

## How it works

Two PacketEvents listeners do all the work.

**Clientbound** (`ClientboundOffsetListener`, priority `HIGHEST`, so positions produced by other
plugins are translated too) adds the offset to:

- chunks - chunk data, unload chunk, view position, light updates, chunk biomes
- blocks - block change, multi block change, block action, break animation, block entity data,
  digging acknowledgements, sign editor, bed
- the player - position and look (respecting relative teleport flags), vehicle move, world spawn,
  and the last death position carried by join/respawn
- entities - all spawn packets, teleport, position sync, minecart movement, entity metadata that
  carries a block position, damage source positions, face player
- effects - world events, particles, sounds, explosion centres
- the world border and the 1.21.6 locator bar

**Serverbound** (`ServerboundOffsetListener`, priority `LOWEST`, so every other plugin and any
anti-cheat sees the real numbers) subtracts it again from movement, block placement and digging,
sign edits, block NBT queries, pick block, and command/jigsaw/structure block edits.

Chunk data is handled specially. It is the most frequent packet carrying a position, and decoding
an entire chunk column just to change two numbers would be wasteful, so the chunk X and Z are
patched directly in the packet buffer - every version of that packet since 1.7 starts with those
two plain big endian ints. If another plugin has already decoded the packet, the plugin falls back
to editing the decoded column instead.

## Known limitations

- **Text is not translated.** Anything that prints coordinates as words - `/tp` feedback, a
  hologram, a scoreboard, another plugin's "you are at X Y Z" message, a web map - still shows the
  real numbers. Only the protocol's positional fields are shifted.
- **Lodestone compasses** point at an unshifted target, because the target lives inside item NBT
  rather than in a position field. The needle will be off by the offset.
- **Nether coordinate maths** no longer lines up. Both worlds get the same shift, so the usual
  "divide by eight" relationship between overworld and nether coordinates does not hold in the
  shifted numbers.
- **Very large coordinates.** A player already near the 30 million block world limit could be
  pushed past it by a large offset. Keep the range modest if your server has that kind of map.
- Debug and gametest packets are not translated.
