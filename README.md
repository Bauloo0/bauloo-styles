# Bauloo Styles

Source code of Bauloo Styles, a client-side Fabric mod for Minecraft 1.21.11. It puts texture
styles (crystals, anchors, armor, tools, sky ...), extras and cosmetics into one menu.

Download: https://baulooprac.xyz · Discord: https://discord.gg/bauloo

## Why the source is here

People asked whether the mod is safe. Nothing in the mod is obfuscated, and this is the full source,
so you can read exactly what it does and build the jar yourself.

## What the mod sends over the internet

- It downloads styles and their preview pictures from the
  [bauloo-styles-cloud](https://github.com/Bauloo0/bauloo-styles-cloud) repository and its mirror worker.
- It checks for a newer version (`release/version.json` in the same repository). The normal download
  can update itself; the download without the auto updater only shows a message.
- It sends your Minecraft name to the worker so other players with the mod can see the small icon
  next to your name in the tab list. You can switch this off in Extras.
- It sends a hashed PC id for anonymous usage stats and so that feedback can't be spammed.

It does not read passwords, browser data, Discord tokens or your Minecraft login.

## Build it yourself

You need Java 21.

```
./gradlew build
```

The jar ends up in `build/libs/`.

The built-in texture packs (`src/main/resources/resourcepacks/`) are left out of this repository:
they belong to their creators. The mod builds and runs without them, it just has fewer built-in styles.

## License

All rights reserved, see `src/main/resources/LICENSE.txt`. You may read and build the code to check
it, but not publish copies or modified versions.
