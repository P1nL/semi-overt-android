# Editor Assets

The local editor is integrated from the approved FluxDo-inspired mobile demo.
Its Tiptap engine and Markdown adapters are built from Semi-Overt's web editor.
`vendor/editor-dependencies.LICENSE` retains the editor dependency notices.
`vendor/gsap.min.js` retains GSAP's license header. The supplied archive-button
press implementation is covered by `licenses/archive-button.LICENSE`.

The native ink-mask approach references Felix Ny's InkFlow, Apache-2.0:
https://github.com/felixny/InkFlow/tree/12ff525a5b597874f1ae4adbee6bc4d4cb4ed648
Original license: `licenses/InkFlow.LICENSE`.
The current mask uses an eight-vertex irregular polygon with broad curved corners,
mapped to the viewport aspect ratio and expanding primarily toward its four corners,
instead of the initial noise shader. All supported Android versions use the same
native clipping path, expanding from a 6dp-radius central dot.
