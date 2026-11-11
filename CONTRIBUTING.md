# Contributing to Shadowed Hearts

Bug reports, translations, gameplay feedback, and pull requests are welcome. Search the [issue tracker](https://github.com/JayemCeekay/shadowedhearts/issues) to see whether someone has already reported what you found.

## Report a bug or suggest a feature

- Give your **Shadowed Hearts, Cobblemon, Minecraft, and loader versions**. Include the version of any add-on involved.
- Say where it happened (singleplayer, LAN, or a dedicated server), what you did, what you expected, and what happened instead.
- For a crash, attach the crash report or the relevant section of `logs/latest.log`. For a visual issue, a screenshot and the name of any shader pack help.

For a feature request, describe the player or server-owner experience you want to improve. An example from your world or modpack helps others understand the proposal.

## Set up a development checkout

Fork the repository and clone your fork. For work on the 2.0 release, create your topic branch from upstream’s `2.0.0` branch. Install a JDK 21 and select it as your Gradle JVM if you import the project into an IDE. Minecraft, Cobblemon, and loader versions are pinned in [gradle.properties](gradle.properties).

Build both loader jars from the repository root. The Gradle wrapper downloads Gradle and project dependencies on the first build.

| What to run | Windows PowerShell | macOS and Linux |
| --- | --- | --- |
| Build both loaders | `.\gradlew.bat :fabric:build :neoforge:build` | `sh gradlew :fabric:build :neoforge:build` |
| Launch a Fabric client | `.\gradlew.bat :fabric:runClient` | `sh gradlew :fabric:runClient` |
| Launch a NeoForge client | `.\gradlew.bat :neoforge:runClient` | `sh gradlew :neoforge:runClient` |

The installable jars appear in `fabric/build/libs/` and `neoforge/build/libs/`. Choose the file named `ShadowedHearts-<loader>-<version>.jar`.

## Find the right place for a change

- [common/src/main](common/src/main) contains the Java and Kotlin gameplay code, shared client code, and resources used by both loaders. For a translation, use [en_us.json](common/src/main/resources/assets/shadowedhearts/lang/en_us.json) as the key reference and preserve any placeholders in the strings.
- [fabric/src/main](fabric/src/main) and [neoforge/src/main](neoforge/src/main) contain each loader’s entry points, event hooks, and platform-specific resources.
- The [example Shadow learnset datapack](datapacks/shadowedhearts-shadow-learnsets/README.md) shows how to add Shadow moves to a species through data.

Keep existing registry IDs, advancement IDs, and saved-data keys stable. If a change needs a new format, describe how existing worlds will load it.

## Open a pull request

Keep the diff focused on one change. Describe the old and new behavior, link any related issue, and list the checks you ran. For a gameplay fix, replay the reported steps and give the observed result and loader. For a multiplayer fix, mention which side you tested and whether a second player saw the expected behavior. Screenshots are especially helpful for interface, model, and aura changes.
