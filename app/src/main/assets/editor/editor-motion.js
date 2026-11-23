'use strict';

window.EditorMotion = (() => {
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  function layer(className) {
    const element = document.createElement('div');
    element.className = className; element.setAttribute('aria-hidden', 'true');
    document.querySelector('#phone').append(element);
    return element;
  }
  function animate(element, keyframes, duration = 240) {
    if (reduced.matches) return null;
    return element.animate(keyframes, { duration, easing: 'cubic-bezier(.16, 1, .3, 1)' });
  }
  function position(element, rect, parent) {
    const root = parent.getBoundingClientRect();
    element.style.left = `${rect.left - root.left}px`; element.style.top = `${rect.top - root.top}px`;
  }
  function tableChange(editor, change) {
    if (reduced.matches) return change();
    const before = SemiEditor.tableInfo(editor);
    const old = before?.element?.closest('.tableWrapper') || before?.element;
    const rect = old?.getBoundingClientRect(), copy = before?.element?.cloneNode(true);
    const cells = new Map([...(before?.element?.querySelectorAll('td, th') || [])].map(cell =>
      [cell, { rect: cell.getBoundingClientRect(), copy: cell.cloneNode(true) }]));
    const siblings = new Map([...editor.view.dom.children].map(element => [element, element.getBoundingClientRect()]));
    old?.getAnimations({ subtree: true }).forEach(animation => animation.cancel());
    change();
    const after = SemiEditor.tableInfo(editor);
    const current = after?.element?.closest('.tableWrapper') || after?.element;
    if (current && before && before.cols !== after.cols) {
      // The table keeps its overall width: animate individual cells, not the wrapper.
      for (const cell of after.element.querySelectorAll('td, th')) {
        const previous = cells.get(cell), next = cell.getBoundingClientRect();
        cell.style.transformOrigin = '0 0';
        animate(cell, previous ? [
          { transform: `translate(${previous.rect.left - next.left}px, ${previous.rect.top - next.top}px) scale(${previous.rect.width / next.width}, ${previous.rect.height / next.height})` },
          { transform: 'none' },
        ] : [{ opacity: 0, transform: 'scaleX(.65)' }, { opacity: 1, transform: 'none' }], 300);
        cells.delete(cell);
      }
      for (const { rect: previous, copy: cell } of cells.values()) {
        const ghost = layer('rich-document table-ghost table-cell-ghost');
        const table = document.createElement('table'), row = table.insertRow();
        row.append(cell); table.style.height = '100%';
        cell.style.minWidth = '0'; cell.style.height = 'auto';
        Object.assign(ghost.style, { width: `${previous.width}px`, height: `${previous.height}px` });
        ghost.append(table); position(ghost, previous, document.querySelector('#phone'));
        animate(ghost, [{ opacity: .8, transform: 'scaleX(1)' }, { opacity: 0, transform: 'scaleX(.65)' }], 240)
          .onfinish = () => ghost.remove();
      }
    } else if (current) {
      const next = current.getBoundingClientRect();
      current.style.transformOrigin = '0 0';
      animate(current, rect ? [
        { transform: `translate(${rect.left - next.left}px, ${rect.top - next.top}px) scale(${rect.width / next.width}, ${rect.height / next.height})` },
        { transform: 'none' },
      ] : [{ opacity: 0, transform: 'translateY(-8px) scale(.98)' }, { opacity: 1, transform: 'none' }], 260);
    } else if (copy && rect) {
      const ghost = layer('rich-document table-ghost');
      Object.assign(ghost.style, { width: `${rect.width}px`, height: `${rect.height}px` });
      ghost.append(copy); position(ghost, rect, document.querySelector('#phone'));
      const animation = animate(ghost, [{ opacity: .7, transform: 'scaleY(1)' }, { opacity: 0, transform: 'scaleY(.92)' }], 180);
      animation.onfinish = () => ghost.remove();
    }
    for (const [element, previous] of siblings) {
      if (!element.isConnected || element === current) continue;
      const next = element.getBoundingClientRect(), dy = previous.top - next.top;
      if (Math.abs(dy) > .5) animate(element, [{ transform: `translateY(${dy}px)` }, { transform: 'none' }], 260);
    }
  }
  return { tableChange };
})();
