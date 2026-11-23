'use strict';

window.EditorContextTools = (() => {
  const drawings = {
    check: '<path d="m5 12 4 4L19 6"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    minus: '<path d="M5 12h14"/>',
    header: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 10h18M9 10v10M15 10v10"/><path d="M6 7h12"/>',
    row: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M3 15h18"/><path d="M6 12h12" stroke-width="3"/>',
    column: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M9 4v16M15 4v16"/><path d="M12 7v10" stroke-width="3"/>',
    cell: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 12h18M12 4v16"/><path d="M6 8h3" stroke-width="3"/>',
    trash: paths.trash,
    link: paths.link,
  };
  const glyph = name => `<svg viewBox="0 0 24 24" aria-hidden="true">${drawings[name]}</svg>`;
  const button = (action, label, name) =>
    `<button type="button" data-context-action="${action}" aria-label="${label}">${glyph(name)}</button>`;
  const host = document.createElement('div');
  host.innerHTML = `
    <div id="table-setup" class="context-popover table-setup" role="toolbar" aria-label="表格规格" hidden>
      ${button('row-less', '减少行数', 'minus')}<output id="table-rows" aria-label="行数">3</output>${button('row-more', '增加行数', 'plus')}
      <span class="table-times" aria-hidden="true">×</span>
      ${button('col-less', '减少列数', 'minus')}<output id="table-cols" aria-label="列数">3</output>${button('col-more', '增加列数', 'plus')}
      <span class="context-divider"></span>${button('header', '表头', 'header')}
      ${button('table-confirm', '确认表格', 'check')}
    </div>
    <div id="table-actions" class="context-popover" role="toolbar" aria-label="表格选区" hidden>
      ${button('scope-row', '选择行', 'row')}${button('scope-column', '选择列', 'column')}${button('scope-cell', '选择单元格', 'cell')}
      <span class="context-divider"></span>${button('table-add', '下方添加行', 'plus')}${button('table-delete', '删除表格', 'trash')}
    </div>
    <div id="link-popover" class="context-popover link-popover" role="group" aria-label="链接网址" hidden>
      <span class="context-link-icon">${glyph('link')}</span>
      <input id="link-url" type="url" inputmode="url" aria-label="网站链接" placeholder="https://" autocomplete="off" spellcheck="false">
      ${button('link-confirm', '确认链接', 'check')}
    </div>
    <div id="link-caret" class="link-caret" aria-hidden="true" hidden>${glyph('link')}</div>
    <div id="table-handles" hidden>
      <button type="button" class="table-selection-handle" data-table-handle="anchor" aria-label="调整表格选区起点"></button>
      <button type="button" class="table-selection-handle" data-table-handle="head" aria-label="调整表格选区终点"></button>
    </div>
    <div id="table-selection-overlay" aria-hidden="true" hidden><div id="table-selection-ink"></div></div>
    <div id="heading-popover" class="context-popover heading-popover" role="menu" aria-label="段落格式" hidden>
      <button type="button" data-heading-level="0" role="menuitemradio" aria-checked="false">正文</button>
      ${['一', '二', '三', '四', '五'].map((name, index) => `<button type="button" data-heading-level="${index + 1}" role="menuitemradio" aria-checked="false">H${index + 1} - ${name}级标题</button>`).join('')}
    </div>`;
  phone.append(host);
  const setup = $('#table-setup'), tableActions = $('#table-actions'), linkPanel = $('#link-popover');
  const linkInput = $('#link-url'), caret = $('#link-caret'), handles = $('#table-handles');
  const headingPanel = $('#heading-popover'), selectionOverlay = $('#table-selection-overlay');
  const context = { table: null, scope: 'all', anchor: null, link: false, heading: null };
  let scheduled = false, press = null, drag = null, suppressCellClick = false;

  function visibleBounds() {
    const root = phone.getBoundingClientRect(), viewport = window.visualViewport;
    const keyboard = parseFloat(getComputedStyle(phone).getPropertyValue('--keyboard-inset')) || 0;
    return { root, left: Math.max(root.left, viewport?.offsetLeft || 0) + 8,
      right: Math.min(root.right, (viewport?.offsetLeft || 0) + (viewport?.width || innerWidth)) - 8,
      top: Math.max($('.header').getBoundingClientRect().bottom + 4, viewport?.offsetTop || 0),
      bottom: Math.min(root.bottom - keyboard, (viewport?.offsetTop || 0) + (viewport?.height || innerHeight)) - 8 };
  }
  function place(panel, target) {
    if (!target || panel.hidden) return;
    const bounds = visibleBounds(), width = panel.offsetWidth, height = panel.offsetHeight;
    const x = Math.max(bounds.left, Math.min((target.left + target.right - width) / 2, bounds.right - width));
    const y = Math.max(bounds.top, Math.min(target.top - height - 10, bounds.bottom - height));
    panel.style.left = `${x - bounds.root.left}px`;
    panel.style.top = `${y - bounds.root.top}px`;
  }
  function selectionRect(info) {
    const elements = [...info.element.querySelectorAll('td.selectedCell, th.selectedCell')];
    if (!elements.length) return info.element.getBoundingClientRect();
    const rects = elements.map(cell => cell.getBoundingClientRect());
    return { left: Math.min(...rects.map(r => r.left)), top: Math.min(...rects.map(r => r.top)),
      right: Math.max(...rects.map(r => r.right)), bottom: Math.max(...rects.map(r => r.bottom)) };
  }
  function positionHandles(rect) {
    const { root, left, right, top, bottom } = visibleBounds();
    for (const [name, x, y] of [['anchor', rect.left, rect.top], ['head', rect.right, rect.bottom]]) {
      const handle = handles.querySelector(`[data-table-handle="${name}"]`);
      handle.style.left = `${Math.max(left, Math.min(x - 14, right - 28)) - root.left}px`;
      handle.style.top = `${Math.max(top, Math.min(y - 14, bottom - 28)) - root.top}px`;
    }
  }
  function positionSelection(rect) {
    const root = phone.getBoundingClientRect(), body = $('#rich-editor').getBoundingClientRect();
    selectionOverlay.style.clipPath = `inset(${Math.max(0, body.top - root.top)}px ${Math.max(0, root.right - body.right)}px ${Math.max(0, root.bottom - body.bottom)}px ${Math.max(0, body.left - root.left)}px)`;
    const ink = $('#table-selection-ink');
    const target = `translate(${rect.left - root.left}px, ${rect.top - root.top}px) scale(${(rect.right - rect.left) / 100}, ${(rect.bottom - rect.top) / 100})`;
    if (selectionOverlay.hidden) {
      ink.style.transform = target; selectionOverlay.hidden = false;
      if (!reducedMotion.matches) ink.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 160 });
    } else ink.style.transform = target;
  }
  function ensureCaretVisible() {
    const keyboard = parseFloat(getComputedStyle(phone).getPropertyValue('--keyboard-inset')) || 0;
    if (!keyboard || (!editor.isFocused && !context.link)) return;
    const range = SemiEditor.getLinkDraft(editor);
    const pos = context.link && range ? range.to : editor.state.selection.head;
    const rect = editor.view.coordsAtPos(pos), scroller = $('#rich-editor');
    const top = scroller.getBoundingClientRect().top + 8;
    const bottom = Math.min(visibleBounds().bottom, island.getBoundingClientRect().top - 12);
    if (rect.bottom > bottom) scroller.scrollTop += rect.bottom - bottom;
    else if (rect.top < top) scroller.scrollTop -= top - rect.top;
  }
  function renderPosition() {
    scheduled = false;
    ensureCaretVisible();
    if (context.table) {
      const info = SemiEditor.tableInfo(editor);
      if (!info?.element || (context.table === 'manage' && !info.cellSelection)) {
        closeTable(); return;
      }
      if (context.table === 'setup') {
        $('#table-rows').value = String(info.rows); $('#table-cols').value = String(info.cols);
        $('[data-context-action="header"]').setAttribute('aria-pressed', String(info.header));
        $('[data-context-action="row-less"]').disabled = info.rows <= 1;
        $('[data-context-action="col-less"]').disabled = info.cols <= 1;
        $('[data-context-action="row-more"]').disabled = info.rows >= 20;
        $('[data-context-action="col-more"]').disabled = info.cols >= 10;
        place(setup, info.element.getBoundingClientRect());
      } else {
        const rect = selectionRect(info);
        place(tableActions, rect); positionHandles(rect); positionSelection(rect);
      }
      if (info.element.closest('.tableWrapper')?.getAnimations().length) update();
    }
    if (context.link) {
      const range = SemiEditor.getLinkDraft(editor);
      if (!range) return closeLink();
      const rect = editor.view.coordsAtPos(range.to), { root } = visibleBounds();
      place(linkPanel, rect);
      caret.style.left = `${rect.left - root.left}px`;
      caret.style.top = `${rect.top - root.top}px`;
      caret.style.height = `${Math.max(16, rect.bottom - rect.top)}px`;
    }
    if (context.heading?.isConnected) {
      place(headingPanel, context.heading.getBoundingClientRect());
      const anchor = context.heading.getBoundingClientRect(), bounds = visibleBounds();
      headingPanel.style.transformOrigin = `${Math.max(12, Math.min(headingPanel.offsetWidth - 12,
        (anchor.left + anchor.right) / 2 - bounds.root.left - parseFloat(headingPanel.style.left)))}px 100%`;
    }
  }
  function update() {
    if (scheduled) return;
    scheduled = true; requestAnimationFrame(renderPosition);
  }
  function closeTable() {
    context.table = null;
    setPopover(setup, false); setPopover(tableActions, false);
    handles.hidden = true; selectionOverlay.hidden = true; phone.classList.remove('table-managing');
  }
  function closeLink() {
    if (!context.link) return;
    context.link = false;
    if (document.activeElement === linkInput) {
      linkInput.blur(); window.SemiNative?.hideKeyboard();
    }
    SemiEditor.setLinkDraft(editor, null);
    setPopover(linkPanel, false); caret.hidden = true; phone.classList.remove('link-editing');
    updateTools();
  }
  function closeHeading() { context.heading = null; setPopover(headingPanel, false); }
  function close() { closeTable(); closeLink(); closeHeading(); }
  function startHeading(button) {
    if (context.heading) { closeHeading(); return; }
    close(); closeEditingPopovers(); closeMenu();
    context.heading = button;
    headingPanel.querySelectorAll('[data-heading-level]').forEach(item => {
      const level = Number(item.dataset.headingLevel);
      item.setAttribute('aria-checked', String(level ? editor.isActive('heading', { level }) : editor.isActive('paragraph')));
    });
    setPopover(headingPanel, true); update();
  }
  function prepare() {
    close(); closeEditingPopovers(); closeMenu(); setExpanded(false);
  }
  function insertTable() {
    prepare();
    if (SemiEditor.tableInfo(editor)) {
      manageTable('all'); return;
    }
    EditorMotion.tableChange(editor, () => editor.commands.insertTable({ rows: 3, cols: 3, withHeaderRow: false }));
    context.table = 'setup'; setPopover(setup, true); update();
  }
  function startLink() {
    prepare();
    const href = editor.getAttributes('link').href || '';
    if (editor.isActive('link')) editor.commands.extendMarkRange('link');
    const { from, to } = editor.state.selection;
    context.link = true; linkInput.value = href;
    SemiEditor.setLinkDraft(editor, { from, to });
    phone.classList.add('link-editing'); caret.hidden = false;
    setPopover(linkPanel, true); updateTools(); update();
  }
  function confirmLink() {
    let href = linkInput.value.trim();
    if (!/^[a-z][a-z\d+.-]*:/i.test(href)) href = `https://${href}`;
    try { if (!['http:', 'https:'].includes(new URL(href).protocol)) throw new Error(); }
    catch { notify('请输入有效网址'); return; }
    const range = SemiEditor.getLinkDraft(editor);
    const chain = editor.chain().setTextSelection(range);
    if (range.to > range.from) chain.setLink({ href }).setTextSelection(range.to);
    else chain.insertContent({ type: 'text', text: href, marks: [{ type: 'link', attrs: { href } }] });
    chain.unsetMark('link').run();
    closeLink();
  }
  function manageTable(scope, anchor = context.anchor, head = anchor) {
    const entering = context.table !== 'manage';
    context.table = 'manage'; context.scope = scope;
    if (!SemiEditor.selectTableRange(editor, scope, anchor, head)) { closeTable(); return; }
    const info = SemiEditor.tableInfo(editor);
    context.anchor = anchor ?? info.start + info.map.map[0];
    if (entering) { blurTextInput(); window.SemiNative?.hideKeyboard(); }
    setPopover(setup, false); setPopover(tableActions, true);
    handles.hidden = false; phone.classList.add('table-managing');
    for (const name of ['row', 'column', 'cell']) {
      $(`[data-context-action="scope-${name}"]`).setAttribute('aria-pressed', String(name === scope));
    }
    $('[data-context-action="table-add"]').setAttribute('aria-label', scope === 'column' ? '右侧添加列' : '下方添加行');
    $('[data-context-action="table-delete"]').setAttribute('aria-label',
      { all: '删除表格', row: '删除选中行', column: '删除选中列', cell: '清空选中单元格' }[scope]);
    update();
  }
  const commands = {
    'table-confirm': closeTable,
    'link-confirm': confirmLink,
    'table-add': () => changeTable(false),
    'table-delete': () => changeTable(true),
  };
  function changeTable(remove) {
    const scope = context.scope;
    const before = SemiEditor.tableInfo(editor);
    const exhausted = remove && before && (scope === 'row' && before.rect.bottom - before.rect.top === before.rows ||
      scope === 'column' && before.rect.right - before.rect.left === before.cols);
    const command = exhausted ? 'deleteTable' : remove ? { all: 'deleteTable', row: 'deleteRow', column: 'deleteColumn', cell: 'deleteSelection' }[scope] :
      scope === 'column' ? 'addColumnAfter' : 'addRowAfter';
    EditorMotion.tableChange(editor, () => editor.commands[command]());
    const info = SemiEditor.tableInfo(editor);
    if (!info) { closeTable(); return; }
    const row = Math.min(info.rect.top, info.rows - 1), col = Math.min(info.rect.left, info.cols - 1);
    const anchor = info.start + info.map.map[row * info.cols + col];
    manageTable(scope, anchor);
  }
  host.addEventListener('pointerdown', event => {
    if (event.target.closest('button')) event.preventDefault();
  });
  host.addEventListener('click', event => {
    if (event.target.closest('button')) preserveToolbarKeyboard();
    const heading = event.target.closest('[data-heading-level]');
    if (heading) {
      const level = Number(heading.dataset.headingLevel);
      if (level) editor.commands.setHeading({ level });
      else editor.commands.setParagraph();
      closeHeading(); updateTools(); return;
    }
    const action = event.target.closest('[data-context-action]')?.dataset.contextAction;
    if (!action) return;
    if (commands[action]) commands[action]();
    else if (action.startsWith('scope-')) manageTable(action.slice(6), context.anchor);
    else {
      const info = SemiEditor.tableInfo(editor);
      if (!info) return;
      const rows = info.rows + (action === 'row-more' ? 1 : action === 'row-less' ? -1 : 0);
      const cols = info.cols + (action === 'col-more' ? 1 : action === 'col-less' ? -1 : 0);
      EditorMotion.tableChange(editor, () => SemiEditor.resizeNewTable(editor, Math.max(1, Math.min(20, rows)), Math.max(1, Math.min(10, cols)),
        action === 'header' ? !info.header : info.header));
      update();
    }
  });
  linkInput.addEventListener('keydown', event => {
    if (event.key === 'Enter') { event.preventDefault(); confirmLink(); }
  });
  function cellAt(x, y) { return document.elementFromPoint(x, y)?.closest('.tiptap td, .tiptap th'); }
  function cancelPress() { if (press) clearTimeout(press.timer); press = null; }
  document.addEventListener('pointerdown', event => {
    suppressCellClick = false;
    if (context.heading && !event.target.closest('#heading-popover, [data-tool="heading"]')) closeHeading();
    const handle = event.target.closest('[data-table-handle]');
    if (handle) {
      event.preventDefault();
      const info = SemiEditor.tableInfo(editor), r = info.rect;
      drag = { handle: handle.dataset.tableHandle, anchor: info.start + info.map.map[r.top * info.cols + r.left],
        head: info.start + info.map.map[(r.bottom - 1) * info.cols + r.right - 1] };
      handle.setPointerCapture(event.pointerId); return;
    }
    const cell = event.target.closest('.tiptap td, .tiptap th');
    if (!cell) {
      if (!event.target.closest('.context-popover, #table-handles, #island') && !event.target.closest('.tiptap')) close();
      else if (context.table && event.target.closest('.tiptap')) closeTable();
      return;
    }
    const pos = SemiEditor.cellPosition(editor, cell);
    // Own the candidate tap so the system's later long-press cannot replace a CellSelection.
    event.preventDefault();
    if (context.table === 'manage') {
      event.preventDefault();
      manageTable(context.scope === 'all' ? 'cell' : context.scope, pos);
      drag = { anchor: pos, head: pos, handle: 'head' };
      suppressCellClick = true; return;
    }
    press = { x: event.clientX, y: event.clientY, pos, timer: setTimeout(() => {
      const anchor = press?.pos;
      if (anchor == null) return;
      closeLink(); manageTable('all', anchor);
      drag = { anchor, head: anchor, handle: 'head' };
      suppressCellClick = true;
    }, 420) };
  }, true);
  document.addEventListener('pointermove', event => {
    if (press && !drag && Math.hypot(event.clientX - press.x, event.clientY - press.y) > 8) cancelPress();
    if (!drag) return;
    if (press && Math.hypot(event.clientX - press.x, event.clientY - press.y) < 8) return;
    event.preventDefault();
    const cell = cellAt(event.clientX, event.clientY);
    if (!cell) return;
    const pos = SemiEditor.cellPosition(editor, cell);
    if (drag.handle === 'anchor') drag.anchor = pos; else drag.head = pos;
    const scope = context.scope === 'all' ? 'cell' : context.scope;
    manageTable(scope, drag.anchor, drag.head);
  }, { capture: true, passive: false });
  document.addEventListener('pointerup', event => {
    if (drag) event.preventDefault();
    else if (press) {
      const hit = editor.view.posAtCoords({ left: press.x, top: press.y });
      editor.commands.focus(hit?.pos ?? press.pos + 2);
    }
    cancelPress(); drag = null;
  }, true);
  document.addEventListener('pointercancel', () => { cancelPress(); drag = null; }, true);
  document.addEventListener('touchstart', event => {
    if (event.target.closest('.tiptap td, .tiptap th')) event.preventDefault();
  }, { capture: true, passive: false });
  document.addEventListener('touchmove', event => { if (drag) event.preventDefault(); }, { passive: false });
  document.addEventListener('click', event => {
    if (!suppressCellClick || !event.target.closest('.tiptap td, .tiptap th')) return;
    event.preventDefault(); event.stopImmediatePropagation(); suppressCellClick = false;
  }, true);
  document.addEventListener('contextmenu', event => {
    if (event.target.closest('.tiptap table')) event.preventDefault();
  });
  $('#rich-editor').addEventListener('scroll', update, { passive: true });
  window.visualViewport?.addEventListener('resize', update);
  window.visualViewport?.addEventListener('scroll', update);
  window.addEventListener('resize', update);
  return { insertTable, startLink, startHeading, update, close,
    back() { if (!context.table && !context.link && !context.heading) return false; close(); return true; } };
})();
