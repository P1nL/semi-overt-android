# Third-party asset provenance

## Current guest identity artwork

The guest avatars use the supplied `system-outline-2815-ghost-loop-cycle.json`
(Ghost, `loop-cycle`), retained at `app/src/main/assets/identity/startup-ghost.json`.
It is rendered by Lottie for Android (Apache 2.0). The static guest avatar fallback
in `app/src/main/res/drawable/ic_guest_ghost.xml` is derived from its first frame.

## Brand opening motion

The four-point star and connected `Vert` paths follow the user's approved lettering
draft and animation preview. `BrandIntroArtwork` draws them with native Compose paths.
The handwriting speed curve references the opening animation at
https://demos.gsap.com/ and is evaluated by `BrandIntroMotion`; no GSAP runtime is
bundled in the Android application.

## Historical identity animation assets

The retained historical assets came from the existing Semi-Overt design export at
`src/shared/assets/lottie/login-attribution.json` in the Semi-Overt web project,
which is played there through Lordicon (`hover-attribution`).

- Original source data retained at `app/src/main/assets/identity/source-login-attribution.json`.
- Sprite export from the approved SO.pen V17 identity asset: 90 frames at 60 fps,
  480 × 480 pixels per frame, 1.5 seconds at normal speed.
- `guest-frames-0.png` through `guest-frames-2.png` and `guest-still.png` are derived
  from that source, not newly authored Android icons.

No separate license grant was supplied with the copied source. This notice records
provenance, not permission to redistribute the source under a new license; confirm
the original asset's applicable license before a public app release.

Other in-app cover illustrations and line icons are implemented in repository code.

## YuFanXinYu interface numerals

The supplied YuFanXinYu (余繁新语) Regular and Bold fonts by chilingg are bundled
as `0`–`9` subsets with raised and emboldened glyphs under the MIT License. The
record calendar and profile counts also use an ExtraBold subset derived from the
adjusted Bold outlines. The license is packaged at `app/src/main/assets/licenses/yufanxinyu.txt`. Article titles and body copy
keep their existing typography. Shanghai Modeng is not bundled.
