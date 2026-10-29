# Friend GUI packs

The GUI assets of the original FriendSystem (button icons, head models and the glyphs that draw the menu backgrounds),
ported from `friends/ref/Resourcepack` to Minecraft 26.x. There is one pack per place it can be installed:

| Folder | Zip | Goes |
|---|---|---|
| `vanilla` | `friends-gui-vanilla.zip` | a resource pack: host it and set it in `server.properties`, or send it yourself |
| `nexo` | `friends-gui-nexo.zip` | unpacked into `plugins/Nexo` (`items/`, `glyphs/`, `pack/`) |
| `itemsadder` | `friends-gui-itemsadder.zip` | unpacked into `plugins/ItemsAdder` (`contents/friendsystem/`) |

`./gradlew :friends:resourcepack:zipPacks` writes the zips, and a `.sha256` beside each, to `build/libs/friends-packs`. The
same files make the same zip, so a checksum only changes when a pack does. `:friends:resourcepack:test` checks the packs
(every file reads, every model, texture, glyph and item they name is there, and the three packs agree on the glyphs).

To publish, attach the zips and their `.sha256` files to a release. XaliMenu downloads them from there when `packs.handler`
in its `config.yml` is `nexo` or `itemsadder` (default `none`, which downloads nothing), see
[menu/README.md](../../menu/README.md). The packs are not bundled in any plugin jar.

## Pack formats

The resource pack format of 26.1 (and its patches) is 84.0, 26.2 is 88.0 and 26.3 is 97.1. The vanilla pack says
`"min_format": 84, "max_format": 97` and nothing else (`pack_format` and `supported_formats` are refused beside them from
format 65 on), which covers all three. One pack is enough because what it depends on is the same in all of them: the client
jars of 26.1, 26.2 and 26.3 were compared, and `items/paper.json`, `items/player_head.json`, `models/item/generated.json`,
`models/item/template_skull.json`, the `range_dispatch` item model and its `custom_model_data` property, the font and
atlas formats, and the pack metadata codec are identical (only an error message in the metadata codec differs). Models
look their textures up in the `items` atlas first and then in `blocks`, in 26.1 and in 26.3 alike.

Nexo and ItemsAdder write their own `pack.mcmeta` with overlays per format when they build the pack (Nexo has
`nexo_26_1`, `nexo_26_2` and `nexo_26_3` and ItemsAdder `ia_overlay_26_1_plus` and `ia_overlay_26_2_plus`), so the
formats are theirs to handle there. To check a new Minecraft version, read `pack_version` in the `version.json` inside its
client jar and diff the files listed above.

## What changed from the original

- **`pack.mcmeta`**: `pack_format: 15` became the format range above.
- **Item models**: `models/item/paper.json` and `models/item/player_head.json` picked a model by `overrides` on
  `custom_model_data`, which the game has ignored since 1.21.4. `items/paper.json` and `items/player_head.json` now do it with
  a `range_dispatch` on `minecraft:custom_model_data` (the same thresholds, 3700-3716 for the buttons, 5000 and 10050 for
  the heads); the vanilla definition is the fallback. The legacy `models/item` and `models/block` files are gone.
- **Heads**: the two head variants are `special` models on the base models `party:item/little_head` and `big_head`, which
  now only hold their `display` transform. The original also had an override for `skeleton_skull` (5050) pointing at a model
  that was never in the pack, so it was not carried over.
- **Atlas**: the textures sit outside `textures/item`, so `atlases/items.json` adds them (`party/buttons` and
  `required/particle`). The old `blocks.json` also listed twelve textures the pack never had.
- **Nexo**: the Oraxen folder became Nexo's own layout. Items use `itemname` and `Pack: texture:` or `model:` (no
  `custom_model_data`: Nexo gives every item its own `item_model`), and the textures and models are under
  `pack/assets/minecraft/` rather than Oraxen's `pack/textures` and `pack/models`, which Nexo only converts from an actual
  Oraxen folder. Item ids are the original's (`like`, `empty`, `sort`, ...), so check them against your other Nexo items.
- **Glyphs**: the original packs disagreed about `anvil`: 40 in the vanilla font, 37 in Oraxen's and ItemsAdder's. All three
  use 40 now. In the vanilla pack the glyphs are in `minecraft:default`; **Nexo puts them in its `nexo:default` font**, so
  text that uses them needs `"font": "nexo:default"` there. ItemsAdder puts them in its own font as well.

## Not done

- No client was available, so what is checked is the structure, not how it looks: Nexo and ItemsAdder were run with the packs
  on a 26.2 server and generated their packs without complaints, and the vanilla pack is checked by the tests above.
- The `ItemsAdder` folder is the original's, which Nexo and ItemsAdder both handle for 26.x; its `big_head` and
  `little_head` models are leftovers that no item uses.
- Viaversion-style packs per client version, and detecting the client's version, are not supported.
