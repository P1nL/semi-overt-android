# Third-party asset provenance

## Current guest identity icon

The current guest intro and resting avatar are native vector renderings of the
user-supplied SVG/Svelte snippet (a user outline with a 600ms head/body bounce),
adapted to keep the shoulders stationary after rising and bounce only the head.
They do not load or play the historical PNG sprites below.

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
