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

`config.yml` is documented inline. The parts worth knowing:

- `enabled-by-default` - whether new players get randomised coordinates without asking.
- `offset.min` / `offset.max` - the range the random shift is drawn from, per axis, in blocks.
  Defaults to `-10000` .. `10000`.
- `offset.minimum-distance` - refuses to roll anything smaller than this, so a random roll cannot
  land on a useless shift of 32 blocks.
- `persistence.enabled` - remember each player's offset in `data.yml` so their coordinates look the
  same every session.
- `persistence.reroll-on-join` - roll a fresh offset on every login instead.
- `apply.kick-on-change` - see above.
- `messages.*` - every player facing string.

Offsets are always rounded to a multiple of 16. A shift that is not chunk aligned cannot be
expressed in the packets that carry chunk coordinates, and would tear the client's world apart at
every chunk border.

The Y axis is deliberately never shifted: the client is told the world's height range once, and
moving chunk sections outside of it breaks lighting and rendering. A base is given away by X and Z,
not by Y.

## Installing

1. Drop `PrivacyCoords-<version>.jar` into `plugins/`.
2. Start the server. That's it - PacketEvents is bundled inside the jar, relocated so it cannot
   clash with other plugins that ship their own copy.

Requires Java 17+ and a Spigot/Paper based server. PacketEvents supports 1.8 through current
releases; PrivacyCoords is developed against modern versions and is most thoroughly exercised
there.

## Building

```
mvn clean package
```

The shaded jar lands in `target/PrivacyCoords-<version>.jar`.

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
