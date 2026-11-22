# Friend GUI packs

**The friend GUI packs** hold the GUI assets of the original FriendSystem (button icons, head models and the glyphs that
draw the menu backgrounds), ported from `friends/ref/Resourcepack` to Minecraft 26.x. They are the resource packs behind
the menus of [XaliFriends](../README.md). The packs are built as zips and are not bundled in any plugin jar.

## Contents

- [Installation](#installation): [Packs](#packs), [Building](#building), [Publishing](#publishing)
- [Configuration](#configuration): [Pack formats](#pack-formats), [Nexo and ViaVersion](#nexo-and-viaversion)
- [What changed from the original](#what-changed-from-the-original)
- [Troubleshooting](#troubleshooting)
- [Development](#development)
- [See also](#see-also)

## Installation

### Packs

There is one pack per place it can be installed:

| Folder | Zip | Goes |
|---|---|---|
| `vanilla` | `friends-gui-vanilla.zip` | A resource pack: host it and set it in `server.properties`, or send it from a plugin. |
| `nexo` | `friends-gui-nexo.zip` | Unpacked into `plugins/Nexo` (`items/`, `glyphs/`, `pack/`). |
| `itemsadder` | `friends-gui-itemsadder.zip` | Unpacked into `plugins/ItemsAdder` (`contents/friendsystem/`). |

XaliFriends can install the Nexo and ItemsAdder packs itself, see
[The resource pack](../README.md#the-resource-pack). The vanilla pack is installed by hand.

### Building

```console
user $ ./gradlew :friends:resourcepack:zipPacks
```

The zips, and a `.sha256` beside each, are written to `build/libs/friends-packs`. The same files make the same zip, so a
checksum changes only when a pack does.

### Publishing

Attach the zips and their `.sha256` files to a release of the public repository
[Xalitoria/xali-friends-resourcepack](https://github.com/Xalitoria/xali-friends-resourcepack) (the plugin's own repository is
private, and a server downloads without signing in). XaliFriends downloads them from the latest release when `packs.handler`
in its `config.yml` is `nexo` or `itemsadder` (the default is `none`, which downloads nothing); `packs.url` is
`https://github.com/Xalitoria/xali-friends-resourcepack/releases/latest/download`.

## Configuration

### Pack formats

The resource pack format of 26.1 (and its patches) is 84.0, 26.2 is 88.0 and 26.3 is 97.1. The vanilla pack says
`"min_format": 84, "max_format": 97` and nothing else (`pack_format` and `supported_formats` are refused beside them
from format 65 on), which covers all three.

One pack is enough because what it depends on is the same in all of them. The client jars of 26.1, 26.2 and 26.3 were
compared, and these are identical: `items/paper.json`, `items/player_head.json`, `models/item/generated.json`,
`models/item/template_skull.json`, the `range_dispatch` item model and its `custom_model_data` property, the font and
atlas formats, and the pack metadata codec (only an error message in the codec differs). Models look their textures up
in the `items` atlas first and then in `blocks`, in 26.1 and in 26.3 alike.

Nexo and ItemsAdder write their own `pack.mcmeta` with overlays per format when they build the pack (Nexo has
`nexo_26_1`, `nexo_26_2` and `nexo_26_3`, ItemsAdder `ia_overlay_26_1_plus` and `ia_overlay_26_2_plus`), so the formats
are theirs to handle there, see [Nexo and ViaVersion](#nexo-and-viaversion).

To check a new Minecraft version, read `pack_version` in the `version.json` inside its client jar and compare the files
listed above.

### Nexo and ViaVersion

This is read from Nexo 1.29.1 (decompiled) and checked by running it on a 26.2 Folia server, with and without
ViaVersion and ViaBackwards 5.12.2.

- **The format range.** Without Via, Nexo's `pack.mcmeta` covers the server's format upwards (88-199 on 26.2). With
  ViaVersion installed, Nexo asks Via for the oldest and newest client it accepts and widens the range to match
  (`ClientPackFormats`), but never below format 46 (1.21.4), the first with `items/*.json`: on the test server the range
  came out as 46-199. Clients older than 1.21.4 are not covered by Nexo's pack, whatever Via lets in.
- **The overlays.** Nexo adds one overlay per format step (`nexo_1_21_1` to `nexo_26_3`) and puts into each what that
  version needs differently (the tripwire blockstate and the atlases, in 26.x). `Pack.generation.skip_unsupported_overlays`
  drops those outside the range. Everything else stays at the root, in the newest layout. A pack that gets merged in
  keeps its own overlays, and entries that are not Nexo's are kept in `pack.mcmeta`.
- **Imports.** The pack is built from, in this order: Nexo's DefaultPack and RequiredPack (downloaded), every zip or
  folder in `plugins/Nexo/pack/external_packs` (sorted by a leading number, then name), the ModelEngine pack,
  `Pack.import.from_location`, `Pack.import.from_url`, then `plugins/Nexo/pack` (read as a pack of its own, `assets/` and
  all), and Nexo's own items, glyphs and sounds on top. Item definitions that clash are merged by type. The
  `pack.mcmeta` of an import is replaced by Nexo's range, but its overlays are kept. One in `plugins/Nexo/pack` itself
  would be read after the range is set and win, which is why the overlay below is an import.
  `Pack.generation.legacy_atlas`, textures merging and obfuscation run over the result.
- **Per-player packs.** `multipack.yml` can send template packs (`plugins/Nexo/pack/template_packs/<id>`) next to the
  main one, optional per player with `/nexo multipack`, or `required: true` for all. The friend pack is not one: it is
  part of the main pack.
- **Older clients (ViaBackwards).** Nexo turns item models of an older layout into the current one when it reads them,
  and can turn them back into `overrides` for formats below 1.21.4 (`LegacyItemOverridesConverter`), but its range never
  goes that low, so that code does not run. It does not rewrite a model that exists in the current layout only.

That last point matters for exactly one file. `player_head.json` uses the special model `player_head`, which exists from
format 63 (1.21.6). Formats 46-62 (1.21.4 and 1.21.5) call it `head` with `"kind": "player"`; this was read from the
client jars of 1.21.4, 1.21.5, 1.21.6, 1.21.8, 1.21.9 and 1.21.11. The Nexo pack therefore carries
`pack/external_packs/friends-gui`, an import with an overlay `friends_1_21_4` for formats 46-62 that holds the old form.
Nexo merged it into its `pack.mcmeta` and renamed the models it points at the same way as in the root file.

The vanilla pack does not have this overlay: it starts at format 84 and promises nothing below that. Nothing else in it
differs by format: `range_dispatch` on `custom_model_data` and the `items` atlas are the same from 1.21.4.

> [!NOTE]
> No 1.21.4 or 1.21.5 client was available, so the overlay is checked as data (the tests compare it with the root file)
> and not on a client. ItemsAdder was not examined for Via.

## What changed from the original

- **`pack.mcmeta`**: `pack_format: 15` became the format range above.
- **Item models**: `models/item/paper.json` and `models/item/player_head.json` picked a model by `overrides` on
  `custom_model_data`, which the game has ignored since 1.21.4. `items/paper.json` and `items/player_head.json` now do
  it with a `range_dispatch` on `minecraft:custom_model_data` (the same thresholds: 3700-3716 for the buttons, 5000 and
  10050 for the heads) and set `oversized_in_gui: true` so the 2x2 big head and multi-slot buttons are not clipped to a
  16x16 slot in Minecraft 1.21.6+/26.x. The vanilla definition is the fallback. The legacy `models/item` and
  `models/block` files are gone.
- **Heads**: the two head variants are `special` models on the base models `friends:item/little_head` and `big_head`,
  which now hold only their `display` transform. The original also had an override for `skeleton_skull` (5050) pointing
  at a model that was never in the pack, so it was not carried over.
- **Atlas**: the textures sit outside `textures/item`, so `atlases/items.json` adds them (`friends/buttons` and
  `required/particle`). The old `blocks.json` also listed twelve textures the pack never had.
- **Nexo**: the Oraxen folder became Nexo's own layout. Items use `itemname` and `Pack: texture:` or `model:` (no
  `custom_model_data`, since Nexo gives every item its own `item_model`). The textures and models are under
  `pack/assets/minecraft/` rather than Oraxen's `pack/textures` and `pack/models`, which Nexo converts only from an actual
  Oraxen folder. Item ids are the original's with a `friend_` prefix (`friend_like`, `friend_empty`, ...), so they do not
  clash with other Nexo items.
- **No party**: the original's party screens (the `party` and `member` glyphs) are gone, and so are the two backgrounds with a
  party button or a party setting. `friends` and `settings2` are the original's `friends_no_party` and `settings2_no_party`.
- **Glyphs**: the original packs disagreed about `anvil`: 40 in the vanilla font, 37 in Oraxen's and ItemsAdder's. All
  three use 40 now. In the vanilla pack the glyphs are in `minecraft:default`. **Nexo puts them in its `nexo:default`
  font**, so text that uses them needs `"font": "nexo:default"` there. ItemsAdder puts them in its own font as well.

## Troubleshooting

### The icons are missing in game

Reload the plugin that serves the pack (`/nexo reload`, `/iazip`) and have the client download the pack again. For the
vanilla pack, check that `server.properties` (or the plugin that sends it) points at the current zip and its checksum.

### The pack installer reports a checksum mismatch

The `.sha256` file and the zip come from different builds. Rebuild with `zipPacks` and upload both files of the same run
to the release.

### Limits

- No client was available, so what is checked is the structure, not how it looks. Nexo and ItemsAdder were run with the
  packs on a 26.2 server and generated their packs without complaints, and the vanilla pack is checked by the tests.
- The `itemsadder` folder is the original's, which Nexo and ItemsAdder both handle for 26.x. Its `big_head` and
  `little_head` models are leftovers that no item uses.
- Packs per client version, and detecting the client's version, are not supported.

## Development

```console
user $ ./gradlew :friends:resourcepack:test
```

`Packs` in `friends/resourcepack/test` checks that:

- every JSON file reads, and the format range covers every 26.x pack version;
- nothing of the legacy layout is left, item definitions pick models that exist, models find their textures in an atlas,
  glyphs point at textures and language files are flat;
- the Nexo and ItemsAdder items, models and glyphs have their files, the Nexo glyphs are the vanilla ones, and the Nexo
  head overlay matches the root `player_head.json` for clients that call the model `head`.

The folders are the sources: edit them, rebuild the zips and attach the new zips to a release.

## See also

- [XaliFriends](../README.md) — the plugin that uses the packs and installs them
- [XaliMenu](../../menu/README.md) — the menus that draw with the glyphs
- [Root README](../../README.md) — the workspace

## External resources

- [Resource pack format](https://minecraft.wiki/w/Resource_pack#Contents) — the pack formats per Minecraft version
- [Nexo](https://docs.nexomc.com/) and [ItemsAdder](https://itemsadder.devs.beer/) — the plugins that serve the packs
