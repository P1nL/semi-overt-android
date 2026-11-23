/*
 * Press/release code from Animorio's supplied archive-button component.
 * Source and license: reference/archive-button/script.js and LICENZA.txt.
 * Only the button binding is adapted for the editor's tool buttons.
 */
(() => {
  const SPRING = 1.6;
  const PRESS = 0.96;

  function bind(button) {
    const tasto = button.querySelector("[data-press-surface]");
    const ridotto = matchMedia("(prefers-reduced-motion: reduce)").matches;

    function giu() {
      tasto.classList.add("is-premuto");
      gsap.to(tasto, { scale: ridotto ? 1 : PRESS, duration: 0.12, ease: "power2.out", overwrite: "auto" });
    }
    function su() {
      tasto.classList.remove("is-premuto");
      gsap.to(tasto, { scale: 1, duration: 0.5, ease: `back.out(${2.2 * SPRING})`, overwrite: "auto" });
    }

    function releaseIfPressed() {
      if (tasto.classList.contains("is-premuto")) su();
    }
    button.addEventListener("pointerdown", giu);
    button.addEventListener("pointerup", su);
    button.addEventListener("pointerleave", releaseIfPressed);
    button.addEventListener("pointercancel", releaseIfPressed);
    button.addEventListener("keydown", (event) => {
      if (!event.repeat && (event.key === "Enter" || event.key === " ")) giu();
    });
    button.addEventListener("keyup", (event) => {
      if (event.key === "Enter" || event.key === " ") su();
    });
    button.addEventListener("blur", releaseIfPressed);
  }

  window.ArchiveButtonPress = { bind };
})();
