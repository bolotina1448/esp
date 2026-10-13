# PlayerESP (Forge 1.8.9)

Client-side mod: draws boxes around other players, visible through walls.
Red = everyone else, green = your team. Press **J** to toggle (rebind in Options > Controls > PlayerESP).

Target: Minecraft 1.8.9, Forge 11.15.1.2318, Java 8.

## Getting the .jar without building on your computer

1. Create a new repository on github.com (private is fine).
2. On the repo page click **Add file > Upload files**, and drag in everything from this folder,
   including the hidden `.github` folder (if your Mac hides it, press Cmd+Shift+. in Finder).
3. Commit. Open the **Actions** tab; the "Build mod" run starts automatically (takes ~3-5 min).
4. When it shows a green check, open the run and download **PlayerESP-jar** at the bottom.
5. Unzip it and use the jar *without* `-dev` in its name. Put it in your `.minecraft/mods` folder
   (or LabyMod's Forge mods folder) and launch Forge 1.8.9.

If the run fails (red X), open it, copy the error from the "Build" step, and send it back.

## Building locally instead

With Java 17 and Gradle 7.6 installed: `gradle build`. The jar lands in `build/libs/`.
