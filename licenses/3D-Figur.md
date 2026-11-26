# 3D-Figur und 3D-Engine

## Mannequin „X Bot“

- Datei in der App: `app/src/main/assets/figure3d/figure.glb`
- Herkunft: Adobe Mixamo (https://www.mixamo.com), Figur „X Bot“. Bezogen aus den Beispielen des
  three.js-Projekts: https://github.com/mrdoob/three.js/blob/dev/examples/models/gltf/Xbot.glb
- Bearbeitung für FitStreak (`tools/model/build_figure.py`): Animationsclips entfernt, Oberfläche
  in Materialien je Muskelregion aufgeteilt. Geometrie und Skelett sind unverändert.
- Nutzung: Adobe erlaubt laut Mixamo-FAQ, Mixamo-Figuren und -Animationen lizenzfrei in privaten und
  kommerziellen Projekten zu verwenden und in Anwendungen einzubetten. Nicht erlaubt ist, die Figuren
  als eigenständige Dateien weiterzuverbreiten oder zu verkaufen. Solange dieses Repository privat
  bleibt, ist das erfüllt; wer es veröffentlicht, sollte die Modelldatei vorher herausnehmen und
  über das Skript aus der Originalquelle erzeugen lassen.

## Filament

- Bibliotheken: `com.google.android.filament:filament-android` und `gltfio-android`
- Urheber: Google LLC, https://github.com/google/filament
- Lizenz: Apache License 2.0, https://www.apache.org/licenses/LICENSE-2.0
