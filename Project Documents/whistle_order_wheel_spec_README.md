Whistle Order Wheel — Flat Spec Sheet PNG

This repository includes a small generator that exports a 1920×1080 PNG with the flat spec layout for the Trainers' Whistle order wheel (tween menu). It mirrors the in-game layout math (see WhistleSelectionClient) at baseDim = 1080 and draws all boxes at 100% size.

Output file
- Project Documents/whistle_order_wheel_spec_1920x1080.png

How to generate
Option A: Run from your IDE
1) Open the project in IntelliJ IDEA.
2) Locate class: common/src/main/java/com/jayemceekay/shadowedhearts/tools/SpecSheetGenerator.java
3) Run the main() method. The PNG will be saved under the path above.

Option B: Build classes via Gradle, then run with Java (no Minecraft needed)
1) From a terminal at the project root, run:
   Windows:
     .\\gradlew.bat -q :common:classes
     java -cp common\\build\\classes\\java\\main com.jayemceekay.shadowedhearts.tools.SpecSheetGenerator

Notes
- The generator uses plain Java2D (AWT) only. No Minecraft runtime is required.
- The sizes and positions match the design targets at 1920×1080: center 90×90, categories 220×42 with 22 px gap, combat buttons 70×32 with 10 px spacing, position/utility/context 110×28, with context width capped to category width minus 8 px.
- The result is a clear, labeled schematic suitable for handing to an artist as a single reference image.
