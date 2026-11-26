'use strict';

window.PublishSling = {
  bind(root, reduced, reportMotion) {
    const button = root.querySelector('button');
    const icon = root.querySelector('[data-icon="send"]');
    const band = root.querySelector('.sling-band');
    const tension = root.querySelector('.sling-tension');
    const particles = root.querySelector('.sling-particles');
    const pull = { x: 0, y: 0, rotation: 0 };
    const confirmDistance = 100, tapSlop = 4, limit = 112;
    const planeAngle = -45;
    let grip, motion, releaseDirection, suppressClick = false;

    function paint() {
      const length = Math.hypot(pull.x, pull.y);
      const strength = Math.min(length / confirmDistance, 1);
      if (grip?.moved) root.toggleAttribute('data-armed', strength === 1);
      icon.style.transform = pull.rotation ? `rotate(${pull.rotation}deg)` : '';
      const resistance = limit / (limit + length);
      const offsetX = pull.x * resistance, offsetY = pull.y * resistance;
      const distance = length * resistance;
      if (distance < .5) {
        button.style.transform = '';
        band.setAttribute('d', '');
        tension.setAttribute('stroke-dasharray', '0 1');
        return;
      }
      button.style.transform = `translate(${offsetX}px, ${offsetY}px)`;
      const axisX = pull.x / length, axisY = pull.y / length;
      const angle = Math.atan2(axisY, axisX) * 180 / Math.PI;
      const bandPath = [-1, 1].map(side => {
        const normalX = -axisY * side, normalY = axisX * side;
        const startX = normalX * 23 + axisX * 8, startY = normalY * 23 + axisY * 8;
        const endX = offsetX + normalX * 19, endY = offsetY + normalY * 19;
        return `M${startX},${startY}Q${(startX + endX) / 2 - axisX * 3},${(startY + endY) / 2 - axisY * 3} ${endX},${endY}`;
      });
      band.setAttribute('d', bandPath.join(''));
      band.style.opacity = String(Math.min(distance / 12, 1));
      tension.setAttribute('stroke-dasharray', `${strength} ${1 - strength}`);
      tension.setAttribute('transform', `rotate(${angle - strength * 180})`);
      reportMotion();
    }

    function aim(direction) {
      const angle = direction * 180 / Math.PI - planeAngle;
      pull.rotation = (angle + 180) % 360 - 180;
    }

    function burst(direction) {
      for (const [index, offset] of [0, -.16, .17, -.35, .31, -.48, .47].entries()) {
        const dot = document.createElement('i');
        particles.append(dot);
        const angle = direction + offset;
        const point = (distance, scale) =>
          `translate(${Math.cos(angle) * distance}px, ${Math.sin(angle) * distance}px) scale(${scale})`;
        const animation = dot.animate([
          { opacity: .92, transform: point(23, index === 0 ? 1.1 : .7) },
          { opacity: 0, transform: point(112 - index * 10, .25) },
        ], { duration: 320 - index * 12, easing: 'cubic-bezier(.16, 1, .3, 1)' });
        animation.onfinish = () => dot.remove();
      }
    }

    function reset() {
      const pointer = grip?.id;
      grip = null;
      if (pointer !== undefined) {
        suppressClick = true;
        if (button.hasPointerCapture(pointer)) button.releasePointerCapture(pointer);
      }
      motion?.kill();
      particles.replaceChildren();
      root.removeAttribute('data-armed');
      root.removeAttribute('data-launching');
      pull.x = pull.y = pull.rotation = 0;
      paint();
    }

    function launch() {
      if (reduced()) { reset(); return; }
      motion?.kill();
      const direction = releaseDirection ?? Math.PI * .73;
      const axisX = Math.cos(direction), axisY = Math.sin(direction);
      aim(direction);
      root.setAttribute('data-launching', '');
      reportMotion();
      motion = gsap.timeline({ onUpdate: paint, onComplete: () => root.removeAttribute('data-launching') });
      if (releaseDirection === undefined) motion.to(pull, { x: -axisX * 19, y: -axisY * 19, duration: .10, ease: 'power2.out' });
      motion.to(pull, { x: axisX * 30, y: axisY * 30, duration: .13, ease: 'power2.in' })
        .call(() => burst(direction))
        .to(pull, { x: 0, y: 0, rotation: 0, duration: .30, ease: 'elastic.out(1, .62)' });
    }

    button.addEventListener('pointerdown', event => {
      if (event.button !== 0 || grip) return;
      suppressClick = false;
      if (!root.classList.contains('sling') || root.hasAttribute('data-launching') || button.disabled) return;
      const scale = root.getBoundingClientRect().width / root.offsetWidth || 1;
      grip = { id: event.pointerId, x: event.clientX, y: event.clientY, scale, moved: false };
      if (event.isTrusted) button.setPointerCapture(event.pointerId);
    });
    function move(event) {
      if (event.pointerId !== grip?.id) return;
      const offsetX = (event.clientX - grip.x) / grip.scale, offsetY = (event.clientY - grip.y) / grip.scale;
      const length = Math.hypot(offsetX, offsetY);
      if (!grip.moved) {
        if (length < tapSlop) return;
        grip.moved = true;
        motion?.kill();
      }
      pull.x = offsetX; pull.y = offsetY;
      if (length >= tapSlop) aim(Math.atan2(-offsetY, -offsetX));
      paint();
    }
    button.addEventListener('pointermove', move);
    function release(event, cancelled) {
      if (event.pointerId !== grip?.id) return;
      if (!cancelled) move(event);
      const moved = grip.moved;
      const armed = root.hasAttribute('data-armed');
      grip = null;
      if (!moved && !cancelled) return;
      suppressClick = true;
      root.removeAttribute('data-armed');
      if (armed && !cancelled && !button.disabled) {
        releaseDirection = Math.atan2(-pull.y, -pull.x);
        button.click();
        releaseDirection = undefined;
        if (root.hasAttribute('data-launching')) return;
      }
      if (reduced()) reset();
      else motion = gsap.to(pull, { x: 0, y: 0, rotation: 0, duration: .36, ease: 'elastic.out(1, .54)', onUpdate: paint });
    }
    button.addEventListener('pointerup', event => release(event, false));
    for (const name of ['pointercancel', 'lostpointercapture']) button.addEventListener(name, event => release(event, true));
    button.addEventListener('click', event => {
      if (suppressClick && (event.pointerType || event.detail > 0)) event.stopImmediatePropagation();
    });
    return { launch, reset };
  },
};
