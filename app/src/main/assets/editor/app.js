'use strict';

const paths = {
  back: '<path d="m12 5-7 7 7 7M5 12h15"/>',
  up: '<path d="m6 15 6-6 6 6" stroke-width="3"/>',
  more: '<circle cx="5" cy="12" r="1.5" fill="currentColor"/><circle cx="12" cy="12" r="1.5" fill="currentColor"/><circle cx="19" cy="12" r="1.5" fill="currentColor"/>',
  send: '<path d="m3 10 18-7-7 18-3-8-8-3Zm8 3L21 3" transform="translate(-1.75 1.75)" stroke-width="2.2"/>',
  person: '<circle cx="12" cy="7" r="3.5"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/>',
  home: '<path d="m3 11 9-7 9 7M5 10v10h14V10m-9 10v-6h4v6"/>',
  content: '<path d="M10 20H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h13a2 2 0 0 1 2 2v9M8 7h9M8 11h6M8 15h3"/><circle cx="15" cy="20" r=".8" fill="currentColor"/><circle cx="20" cy="20" r=".8" fill="currentColor"/>',
  cursor: '<path d="M8 3c2.5 0 4 .9 4 3 0-2.1 1.5-3 4-3M12 6v12M8 21c2.5 0 4-.9 4-3 0 2.1 1.5 3 4 3M9 12h6" stroke-width="2.8"/>',
  paragraph: '<path d="M14 21V3H9a5 5 0 0 0 0 10h5M19 3v18M11 3h10"/>',
  image: '<rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8" cy="8" r="1" fill="currentColor"/><path d="m4 18 6-6 3 3 3-5 5 7"/>',
  link: '<path d="m10 14 4-4M8 16l-1 1a4 4 0 0 1-6-6l5-5a4 4 0 0 1 6 0m4 2 1-1a4 4 0 0 1 6 6l-5 5a4 4 0 0 1-6 0" transform="translate(1 0) scale(.92)"/>',
  bulletList: '<circle cx="4" cy="6" r="1" fill="currentColor"/><circle cx="4" cy="12" r="1" fill="currentColor"/><circle cx="4" cy="18" r="1" fill="currentColor"/><path d="M9 6h12M9 12h12M9 18h12" stroke-width="2.6"/>',
  numberedList: '<path d="M10 6h11M10 12h11M10 18h11M3 3h2v6M3 9h4M3 14c0-2 4-2 4 0 0 1-4 3-4 5h4"/>',
  quote: '<path d="M3 5h7v8c0 4-3 6-6 6v-3c2 0 3-1 3-3H3V5Zm12 0h7v8c0 4-3 6-6 6v-3c2 0 3-1 3-3h-4V5Z" fill="currentColor" stroke="none"/>',
  underline: '<path d="M6 3v8a6 6 0 0 0 12 0V3M4 21h16"/>',
  strikethrough: '<path d="M16 4H9a4 4 0 0 0-2 7M3 12h18M8 20h7a4 4 0 0 0 2-7" stroke-width="2.6"/>',
  highlight: '<path d="m9 11 8-8 4 4-8 8-4-4Zm0 0-3 3 4 4 3-3M6 14l-3 7 7-3M14 21h7"/>',
  alignLeft: '<path d="M3 4h18M3 9h12M3 14h18M3 19h12"/>',
  alignCenter: '<path d="M3 4h18M6 9h12M3 14h18M6 19h12"/>',
  alignRight: '<path d="M3 4h18M9 9h12M3 14h18M9 19h12"/>',
  code: '<path d="m7 6-5 6 5 6m10-12 5 6-5 6M14 3l-4 18" stroke-width="2.4"/>',
  book: '<path d="M12 5c-2-3-6-3-10-2v17c4-1 8-1 10 2m0-17c2-3 6-3 10-2v17c-4-1-8-1-10 2V5Z"/>',
  cloud: '<path d="M7 18H6a4 4 0 0 1-1-8 7 7 0 0 1 13-2 5 5 0 0 1 0 10h-1M12 20V9m-4 4 4-4 4 4"/>',
  trash: '<path d="M3 5h18M9 5V3h6v2M5 5l1 16h12l1-16M10 9v8M14 9v8"/>',
  pen: '<path d="m15 3 6 6M4 20l1-5L17 3a2 2 0 0 1 4 4L9 19l-5 1Z"/>',
  close: '<path d="m6 6 12 12M6 18 18 6"/>',
  table: '<rect x="2" y="3" width="20" height="18" rx="2"/><path d="M2 9h20M2 15h20M9 3v18M15 3v18"/>',
  separator: '<path d="M3 12h18" stroke-width="2.5"/>',
  undo: '<path d="m8 4-5 5 5 5M3 9h10a7 7 0 0 1 0 14" transform="translate(0 -2)"/>',
  redo: '<g transform="translate(24 0) scale(-1 1)"><path d="m8 4-5 5 5 5M3 9h10a7 7 0 0 1 0 14" transform="translate(0 -2)"/></g>',
  selectAll: '<rect x="6" y="6" width="12" height="12" rx="1"/><path d="M3 7V3h4M17 3h4v4M21 17v4h-4M7 21H3v-4" stroke-dasharray="3 2"/>',
  copy: '<rect x="8" y="8" width="13" height="13" rx="2"/><path d="M15 4V3H3v12h1"/>',
  cut: '<circle cx="6" cy="6" r="3"/><circle cx="6" cy="18" r="3"/><path d="m8 8 13 13M8 16 21 3"/>',
  paste: '<rect x="4" y="4" width="16" height="18" rx="2"/><rect x="8" y="2" width="8" height="5" rx="2"/><path d="M8 12h8M8 17h6"/>',
  left: '<path d="m10 5-7 7 7 7M3 12h18"/>',
  right: '<path d="m14 5 7 7-7 7M3 12h18"/>',
  down: '<path d="m5 14 7 7 7-7M12 3v18"/>',
};

const $ = (selector) => document.querySelector(selector);
const phone = $('#phone'), island = $('#island'), titleInput = $('#topic-title');
const dialog = $('#action-dialog'), cursorButton = $('#cursor-button');
const drawerPages = $('#drawer-pages'), summaryInput = $('#topic-summary');
const MIN_BODY_WORDS = 50;
const draftKey = new URLSearchParams(location.search).get('draftKey') || 'semiovert-editor-draft-v1';
const toolsKey = 'semiovert-editor-prototype-tools-v1';
const tools = [
  { id: 'bold', label: '加粗', letter: 'B', group: 'common' },
  { id: 'italic', label: '斜体', letter: 'I', group: 'common' },
  { id: 'heading', label: '标题', letter: 'H', group: 'common' },
  { id: 'image', label: '图片', icon: 'image', group: 'common' },
  { id: 'link', label: '链接', icon: 'link', group: 'common' },
  { id: 'bulletList', label: '无序列表', icon: 'bulletList', group: 'common' },
  { id: 'numberedList', label: '有序列表', icon: 'numberedList', group: 'common' },
  { id: 'quote', label: '引用', icon: 'quote', group: 'common' },
  { id: 'paragraph', label: '正文', icon: 'paragraph', group: 'format' },
  { id: 'underline', label: '下划线', icon: 'underline', group: 'format' },
  { id: 'strikethrough', label: '删除线', icon: 'strikethrough', group: 'format' },
  { id: 'highlight', label: '高亮', icon: 'highlight', group: 'format' },
  { id: 'alignLeft', label: '左对齐', icon: 'alignLeft', group: 'alignment' },
  { id: 'alignCenter', label: '居中', icon: 'alignCenter', group: 'alignment' },
  { id: 'alignRight', label: '右对齐', icon: 'alignRight', group: 'alignment' },
  { id: 'table', label: '表格', icon: 'table', group: 'insert' },
  { id: 'codeBlock', label: '代码块', icon: 'code', group: 'insert' },
  { id: 'separator', label: '分割线', icon: 'separator', group: 'insert' },
];
const state = { expanded: false, preview: false, customizing: false, selecting: false, page: 0, coverUrl: '', pinned: ['bold', 'italic', 'heading', 'image', 'link'], saved: true };
let editor, body = '', saveTimer, toastTimer, dialogSubmit;
let coverLoad = 0;
let cursorRepeat = null, suppressCursorClick = null;
let selectLabelFrame = 0;
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
const reducePreviewMotion = () => reducedMotion.matches || phone.dataset.motion === 'off';
const popoverAnimations = new WeakMap();
const toolbarInputModes = new WeakMap();
let previewAuthor = { name: '我', avatar: null, variant: 0 };

function icon(name) { return `<svg viewBox="0 0 24 24" aria-hidden="true">${paths[name] || paths.content}</svg>`; }
function hydrateIcons(root = document) { root.querySelectorAll('[data-icon]').forEach((node) => { node.innerHTML = icon(node.dataset.icon); }); }
function escapeHtml(value) { return String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char])); }
function toolIcon(tool) { return tool.letter ? `<span class="letter-icon${tool.id === 'italic' ? ' italic' : ''}">${tool.letter}</span>` : icon(tool.icon); }
function notify(message) {
  $('#toast').textContent = message; $('#toast').classList.add('visible');
  clearTimeout(toastTimer); toastTimer = setTimeout(dismissTip, 2400);
}
function dismissTip() {
  clearTimeout(toastTimer);
  $('#toast').classList.remove('visible');
}
function renderTools() {
  for (const group of ['common', 'format', 'alignment', 'insert']) {
    $(`#${group}-tools`).innerHTML = tools.filter((tool) => tool.group === group).map((tool) =>
      `<button type="button" class="tool-cell${state.pinned.includes(tool.id) ? ' pinned' : ''}" data-tool="${tool.id}" aria-label="${state.customizing ? '固定或取消固定：' : ''}${tool.label}"><span class="tool-surface"><span class="press-surface" data-press-surface aria-hidden="true"></span><span data-tool-icon>${toolIcon(tool)}</span></span><span class="tool-name">${tool.label}</span></button>`
    ).join('');
  }
  document.querySelectorAll('.tool-cell').forEach(ArchiveButtonPress.bind);
  renderQuickTools();
  updateTools();
}
function renderQuickTools() {
  $('#quick-tools').innerHTML = state.pinned.map((id) => tools.find((tool) => tool.id === id)).filter(Boolean).map((tool) =>
    `<button type="button" class="quick-tool" data-tool="${tool.id}" aria-label="${tool.label}"><span class="press-surface" data-press-surface aria-hidden="true"></span>${toolIcon(tool)}</button>`
  ).join('');
  $('#quick-tools').querySelectorAll('.quick-tool').forEach(ArchiveButtonPress.bind);
}
function updateTools() {
  if (!editor) return;
  $('#customize-button').setAttribute('aria-pressed', String(state.customizing));
  const marks = { bold: 'bold', italic: 'italic', heading: 'heading', image: 'image', link: 'link', bulletList: 'bulletList', numberedList: 'orderedList', quote: 'blockquote', paragraph: 'paragraph', underline: 'underline', strikethrough: 'strike', highlight: 'highlight', table: 'table', codeBlock: 'codeBlock' };
  document.querySelectorAll('[data-tool]').forEach((button) => {
    const id = button.dataset.tool;
    const customizing = state.customizing && button.classList.contains('tool-cell');
    const alignment = { alignLeft: 'left', alignCenter: 'center', alignRight: 'right' }[id];
    const active = customizing ? state.pinned.includes(id) :
      alignment ? editor.isActive({ textAlign: alignment }) :
        id === 'link' && phone.classList.contains('link-editing') ? true : marks[id] ? editor.isActive(marks[id]) : false;
    button.classList.toggle('active', active);
    if (button.classList.contains('tool-cell')) {
      button.classList.toggle('pinned', state.pinned.includes(id));
      const tool = tools.find((item) => item.id === id);
      button.setAttribute('aria-label', `${customizing ? '固定或取消固定：' : ''}${tool.label}`);
    }
    button.disabled = !customizing && (id === 'undo' ? !editor.can().undo() : id === 'redo' ? !editor.can().redo() : false);
    if (customizing || alignment || marks[id]) button.setAttribute('aria-pressed', String(active));
  });
  for (const id of ['copy', 'cut']) $(`[data-content-action="${id}"]`).disabled = editor.state.selection.empty;
}
function setPopover(panel, open) {
  if (open === (panel.dataset.open === 'true')) return;
  const offset = panel.id === 'overflow-menu' ? '-6px' : '8px';
  const hiddenTransform = `translateY(${offset}) scale(.97)`;
  const from = panel.hidden ? { opacity: 0, transform: hiddenTransform } :
    { opacity: getComputedStyle(panel).opacity, transform: getComputedStyle(panel).transform };
  popoverAnimations.get(panel)?.cancel();
  popoverAnimations.delete(panel);
  panel.dataset.open = String(open);
  panel.inert = !open;
  panel.setAttribute('aria-hidden', String(!open));
  panel.hidden = false;
  if (reducedMotion.matches) { panel.hidden = !open; return; }
  const to = open ? { opacity: 1, transform: 'translateY(0px) scale(1)' } :
    { opacity: 0, transform: hiddenTransform };
  const animation = panel.animate([from, to], {
    duration: open ? 180 : 140, easing: 'cubic-bezier(.2, .75, .25, 1)', fill: 'forwards',
  });
  popoverAnimations.set(panel, animation);
  animation.onfinish = () => {
    if (popoverAnimations.get(panel) !== animation) return;
    panel.hidden = !open;
    animation.cancel();
    popoverAnimations.delete(panel);
  };
}
function closeEditingPopovers() {
  stopCursorRepeat();
  setPopover($('#editing-actions'), false);
  setPopover($('#cursor-controls'), false);
  $('#content-button').setAttribute('aria-expanded', 'false');
  cursorButton.setAttribute('aria-expanded', 'false');
}
function updateDrawerPage(page) {
  page = Math.max(0, Math.min(1, page));
  if (page !== state.page) {
    const dot = $('#page-indicator .liquid-dot'), from = getComputedStyle(dot).transform;
    dot.getAnimations().forEach((animation) => animation.cancel());
    if (!reducedMotion.matches) dot.animate([
      { transform: from },
      { transform: 'scale(1.28, .86)', offset: .4 },
      { transform: 'scale(1)' },
    ], { duration: 320, easing: 'cubic-bezier(.4, 0, .2, 1)' });
    closeEditingPopovers();
    if (page === 1 && state.customizing) {
      state.customizing = false; island.classList.remove('customizing'); updateTools();
    }
  }
  state.page = page;
  $('#page-indicator').dataset.page = String(page);
  document.querySelectorAll('[data-drawer-page]').forEach((button) => {
    button.setAttribute('aria-pressed', String(Number(button.dataset.drawerPage) === page));
  });
  $('#tools-scroll').inert = !state.expanded || page !== 0;
  $('#details-page').inert = !state.expanded || page !== 1;
}
function goToDrawerPage(page, animate = state.expanded) {
  page = Math.max(0, Math.min(1, page));
  if (!animate) updateDrawerPage(page);
  drawerPages.scrollTo({
    left: drawerPages.clientWidth * page,
    behavior: animate && !reducedMotion.matches ? 'smooth' : 'instant',
  });
}
function blurTextInput() {
  const active = document.activeElement;
  if (active?.matches('input, textarea, [contenteditable="true"]')) active.blur();
}
function suppressTextKeyboard() {
  const active = document.activeElement;
  if (!active?.matches('input, textarea, [contenteditable="true"]')) return;
  if (!toolbarInputModes.has(active)) toolbarInputModes.set(active, active.getAttribute('inputmode'));
  // Keep the native DOM caret and selection while disabling only the soft keyboard.
  active.setAttribute('inputmode', 'none');
}
function preserveToolbarKeyboard() {
  if (!phone.classList.contains('keyboard-open')) suppressTextKeyboard();
}
function scrambleSelectionLabel(text) {
  const label = $('#select-button .select-label'), from = label.textContent;
  cancelAnimationFrame(selectLabelFrame);
  const noise = '<>/\\|{}[]#%01', count = Math.max(from.length, text.length), start = performance.now();
  function frame(now) {
    const progress = reducedMotion.matches ? 1 : Math.min(1, (now - start) / 280);
    const step = Math.floor(progress * 20);
    label.textContent = progress >= 1 ? text : Array.from({ length: count }, (_, index) => {
      const local = progress * 1.6 - index / count * .6;
      return local <= 0 ? from[index] || ' ' : local >= 1 ? text[index] || ' ' :
        noise[(index * 7 + step * 3) % noise.length];
    }).join('').trimEnd();
    selectLabelFrame = progress < 1 ? requestAnimationFrame(frame) : 0;
  }
  if (reducedMotion.matches) { label.textContent = text; selectLabelFrame = 0; }
  else selectLabelFrame = requestAnimationFrame(frame);
}
function setExpanded(expanded) {
  if (expanded === state.expanded) return;
  closeEditingPopovers();
  if (expanded) {
    suppressTextKeyboard();
    window.SemiNative?.hideKeyboard();
  } else if (drawerPages.contains(document.activeElement)) blurTextInput();
  state.expanded = expanded; state.customizing = false;
  island.classList.toggle('expanded', expanded); island.classList.remove('customizing');
  drawerPages.inert = !expanded;
  updateDrawerPage(state.page);
  $('#quick-tools').inert = expanded;
  $('#customize-button').inert = !expanded;
  for (const id of ['#grabber', '#expand-button']) {
    $(id).setAttribute('aria-expanded', String(expanded));
    $(id).setAttribute('aria-label', expanded ? '收起格式工具' : '展开格式工具');
  }
  updateTools();
}
function bodyWordCount() {
  return Array.from(editor.getText({ blockSeparator: '' }).replace(/\s/g, '')).length;
}
function validateArticle() {
  if (!titleInput.value.trim()) { notify('请输入标题'); return false; }
  if (bodyWordCount() < MIN_BODY_WORDS) {
    notify(`正文至少需要 ${MIN_BODY_WORDS} 个字`);
    return false;
  }
  return true;
}
function updateCounts() {
  $('#title-count').textContent = `${Array.from(titleInput.value).length} 字`;
  const count = bodyWordCount();
  $('#body-count-value').textContent = `${count} / ${MIN_BODY_WORDS}`;
  $('#body-count').classList.toggle('valid', count >= MIN_BODY_WORDS);
  titleInput.style.height = '43px';
  titleInput.style.height = `${Math.min(110, Math.max(43, titleInput.scrollHeight))}px`;
}
function saveDraft() {
  clearTimeout(saveTimer);
  try {
    if (window.SemiNativeEditor?.cloud) {
      window.SemiNativeEditor.persistCloud();
    } else {
      localStorage.setItem(draftKey, JSON.stringify({ title: titleInput.value, body, summary: summaryInput.value, coverUrl: state.coverUrl, updatedAt: new Date().toISOString() }));
      $('#save-label').textContent = '自动保存';
    }
    state.saved = true;
  } catch { $('#save-label').textContent = '本机存储已满'; notify('本机存储已满，请导出草稿'); }
}
function changed() {
  state.saved = false; updateCounts(); clearTimeout(saveTimer);
  window.SemiNativeEditor?.cloudChanged();
  saveTimer = setTimeout(saveDraft, 600);
}
function restoreDraft() {
  try {
    const stored = JSON.parse(localStorage.getItem(draftKey) || 'null');
    if (stored && typeof stored.title === 'string' && typeof stored.body === 'string') {
      titleInput.value = stored.title; body = stored.body;
      summaryInput.value = typeof stored.summary === 'string' ? stored.summary.slice(0, 255) : '';
      state.coverUrl = typeof stored.coverUrl === 'string' ? stored.coverUrl : '';
    }
    const pinned = JSON.parse(localStorage.getItem(toolsKey) || 'null');
    if (Array.isArray(pinned)) state.pinned = [...new Set(pinned.filter((id) => tools.some((tool) => tool.id === id)))];
  } catch { notify('草稿无法读取，可重新输入'); }
  updateSummaryCount(); renderCover();
}
function updateSummaryCount() {
  $('#summary-count').textContent = `${Array.from(summaryInput.value).length}/255`;
}
function renderCover() {
  const image = $('#cover-thumbnail'), hasCover = Boolean(state.coverUrl);
  image.hidden = !hasCover;
  if (hasCover) {
    if (image.getAttribute('src') !== state.coverUrl) image.src = state.coverUrl;
  } else image.removeAttribute('src');
  $('#cover-upload').classList.toggle('has-cover', hasCover);
  $('#cover-upload').setAttribute('aria-label', hasCover ? '更换封面' : '上传封面');
  $('#cover-upload-label').textContent = hasCover ? '更换' : '上传';
  $('#remove-cover').hidden = !hasCover;
}
async function readLocalImage(file) {
  if (file.size >= 5 * 1024 * 1024) throw new Error('图片需小于 5 MB');
  const bitmap = await createImageBitmap(file), scale = Math.min(1, 1280 / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas'); canvas.width = Math.round(bitmap.width * scale); canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d').drawImage(bitmap, 0, 0, canvas.width, canvas.height); bitmap.close();
  return canvas.toDataURL('image/jpeg', .8);
}
function mountEditor() {
  editor?.destroy();
  editor = SemiEditor.createEditor({
    element: $('#rich-editor'),
    content: body,
    onUpdate: ({ editor: current }) => {
      body = SemiEditor.serializeEditorToMarkdown(current);
      changed(); updateTools();
    },
    onSelectionUpdate: updateTools,
    onTransaction: () => window.EditorContextTools?.update(),
  });
}
function editingChain() { return editor.chain(); }
function closeMenu() {
  setPopover($('#overflow-menu'), false);
  $('#menu-dismiss').hidden = true;
  $('#more-button').setAttribute('aria-expanded', 'false');
}
function renderPreviewAuthor() {
  $('#preview-author-name').textContent = previewAuthor.name;
  const image = $('#preview-avatar-image');
  const symbol = $('#preview-avatar-symbol');
  symbol.innerHTML = icon(['person', 'book', 'home'][previewAuthor.variant % 3] || 'person');
  if (image.getAttribute('src') === previewAuthor.avatar) return;
  image.hidden = true;
  if (previewAuthor.avatar) image.src = previewAuthor.avatar;
  else image.removeAttribute('src');
}
function renderPreviewDate(timestamp) {
  const stored = new Date(timestamp || NaN);
  const date = Number.isNaN(stored.getTime()) ? new Date() : stored;
  const dateLabel = $('#preview-date');
  dateLabel.dateTime = dateLabel.textContent = [
    date.getFullYear(), String(date.getMonth() + 1).padStart(2, '0'), String(date.getDate()).padStart(2, '0'),
  ].join('-');
}
function renderPreview() {
  $('#preview-title').textContent = titleInput.value || '未命名文章';
  $('#preview-summary').textContent = summaryInput.value;
  $('#preview-summary').hidden = !summaryInput.value.trim();
  $('#preview-cover').hidden = !state.coverUrl;
  if (state.coverUrl) $('#preview-cover').src = state.coverUrl;
  else $('#preview-cover').removeAttribute('src');
  $('#preview-body').innerHTML = SemiEditor.renderMarkdownToEditorHtml(body);
  renderPreviewAuthor();
  renderPreviewDate();
  const minutes = Math.max(1, Math.round(bodyWordCount() / 300));
  $('#preview-duration').textContent = `约 ${minutes} 分钟`;
}
function setPreviewMode(active, animate) {
  state.preview = active;
  if (!animate) phone.classList.add('preview-instant');
  phone.classList.toggle('previewing', active);
  $('#preview').inert = !active;
  $('#writing-area').inert = island.inert = active;
  for (const selector of ['#body-count', '#title-count']) $(selector).setAttribute('aria-hidden', String(active));
  window.SemiNativeEditor?.previewChanged();
  if (!animate) {
    phone.getBoundingClientRect();
    phone.classList.remove('preview-instant');
  }
}
function showPreview(animate = true) {
  closeMenu(); closeEditingPopovers(); window.EditorContextTools?.close(); saveDraft(); blurTextInput();
  window.SemiNative?.hideKeyboard();
  const preview = $('#preview');
  renderPreview();
  preview.hidden = false;
  preview.scrollTop = 0;
  if (animate) preview.getBoundingClientRect();
  $('#back-button').setAttribute('aria-label', '返回编辑');
  setPreviewMode(true, animate);
}
function closePreview(animate = true) {
  if (!state.preview) return;
  sling.reset();
  dismissTip();
  $('#back-button').setAttribute('aria-label', window.SemiNativeEditor?.cloud ? '返回草稿箱' : '返回');
  setPreviewMode(false, animate);
}
function resetDraft() {
  clearTimeout(saveTimer); titleInput.value = body = summaryInput.value = ''; state.coverUrl = ''; state.saved = true;
  coverLoad++; $('#cover-upload').disabled = false; updateSummaryCount(); renderCover();
  localStorage.removeItem(draftKey); closePreview(false); mountEditor(); setExpanded(false);
  updateCounts(); updateTools();
}
function openDialog(title, content, submitLabel, onSubmit) {
  blurTextInput();
  closeMenu(); closeEditingPopovers(); $('#dialog-title').textContent = title; $('#dialog-content').innerHTML = content;
  $('#dialog-actions').innerHTML = `<button type="button" data-action="close-dialog">取消</button>${submitLabel ? `<button type="submit" class="primary">${submitLabel}</button>` : ''}`;
  dialogSubmit = onSubmit; hydrateIcons(dialog); dialog.showModal();
}
function selectedText() { const { from, to } = editor.state.selection; return editor.state.doc.textBetween(from, to, '\n'); }
function runTool(id, fromGrid = false, sourceButton = null) {
  if (state.customizing && fromGrid) {
    state.pinned = state.pinned.includes(id) ? state.pinned.filter((item) => item !== id) : [...state.pinned, id];
    try { localStorage.setItem(toolsKey, JSON.stringify(state.pinned)); } catch { notify('工具设置未能保存'); }
    renderQuickTools(); updateTools(); return;
  }
  if (id === 'link' && phone.classList.contains('link-editing')) {
    window.EditorContextTools.close(); updateTools(); return;
  }
  if (id !== 'heading') window.EditorContextTools?.close();
  const commands = {
    paragraph: () => editingChain().setParagraph().run(),
    bold: () => editingChain().toggleBold().run(),
    italic: () => editingChain().toggleItalic().run(),
    underline: () => editingChain().toggleMark('underline').run(),
    strikethrough: () => editingChain().toggleStrike().run(),
    highlight: () => editingChain().toggleHighlight().run(),
    bulletList: () => editingChain().toggleBulletList().run(),
    numberedList: () => editingChain().toggleOrderedList().run(),
    quote: () => editingChain().toggleBlockquote().run(),
    codeBlock: () => editingChain().toggleCodeBlock().run(),
    separator: () => editingChain().setHorizontalRule().run(),
    alignLeft: () => editingChain().setTextAlign('left').run(),
    alignCenter: () => editingChain().setTextAlign('center').run(),
    alignRight: () => editingChain().setTextAlign('right').run(),
    undo: () => editingChain().undo().run(),
    redo: () => editingChain().redo().run(),
    heading: () => window.EditorContextTools.startHeading(sourceButton || $('#quick-tools [data-tool=heading]')),
    image: () => { setExpanded(false); $('#image-picker').click(); },
    link: () => window.EditorContextTools.startLink(),
    table: () => window.EditorContextTools.insertTable(),
  };
  commands[id]?.(); updateTools();
}
function downloadDraft() {
  const metadata = [`title: ${JSON.stringify(titleInput.value)}`];
  if (summaryInput.value) metadata.push(`summary: ${JSON.stringify(summaryInput.value)}`);
  if (state.coverUrl) metadata.push(`coverUrl: ${JSON.stringify(state.coverUrl)}`);
  const content = `---\n${metadata.join('\n')}\n---\n\n${body}`;
  if (window.SemiNative) {
    SemiNative.exportDraft((titleInput.value.trim() || '未命名文章') + '.md', content);
    return;
  }
  const url = URL.createObjectURL(new Blob([content], { type: 'text/markdown;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url;
  link.download = (titleInput.value.trim() || '未命名文章').replace(/[\\/:*?"<>|]/g, '-') + '.md';
  link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
async function contentAction(id) {
  closeEditingPopovers();
  if (id === 'selectAll') { editingChain().selectAll().run(); return; }
  if (id === 'copy' || id === 'cut') {
    try {
      await navigator.clipboard.writeText(selectedText());
      if (id === 'cut') editingChain().deleteSelection().run();
      else notify('已复制');
    } catch { notify('请使用系统菜单复制或剪切'); }
  } else if (id === 'paste') {
    try {
      const text = await navigator.clipboard.readText();
      if (text) editingChain().insertContent(text.split(/\r?\n/).map((line) => ({
        type: 'paragraph', content: line ? [{ type: 'text', text: line }] : [],
      }))).run();
    } catch {
      openDialog('粘贴文字', '<label class="dialog-field">粘贴内容<textarea name="text" rows="4" placeholder="在此粘贴"></textarea></label>', '插入', (data) => {
        editingChain().insertContent(escapeHtml(data.get('text')).replace(/\r?\n/g, '<br>')).run(); return true;
      });
    }
  }
}
const actions = {
  menu() {
    closeEditingPopovers();
    const opening = $('#overflow-menu').dataset.open !== 'true';
    setPopover($('#overflow-menu'), opening);
    $('#menu-dismiss').hidden = !opening;
    $('#more-button').setAttribute('aria-expanded', String(opening));
  },
  expand() { setExpanded(!state.expanded); },
  export() { closeMenu(); downloadDraft(); },
  back() {
    if (state.preview) return closePreview();
    if (state.expanded) return setExpanded(false);
    saveDraft();
    openDialog('草稿已保留', '<p class="dialog-copy">内容已保存在此浏览器中，可继续编辑或导出 Markdown。</p>', '导出草稿', () => { downloadDraft(); return true; });
  },
  customize() {
    if (state.page !== 0) goToDrawerPage(0);
    state.customizing = !state.customizing; island.classList.toggle('customizing', state.customizing);
    updateTools(); if (state.customizing) notify('点击工具，固定或移出常用栏');
  },
  'content-actions'() {
    const opening = $('#content-button').getAttribute('aria-expanded') !== 'true'; closeEditingPopovers(); closeMenu(); updateTools();
    setPopover($('#editing-actions'), opening); $('#content-button').setAttribute('aria-expanded', String(opening));
  },
  'cursor-controls'() {
    const opening = cursorButton.getAttribute('aria-expanded') !== 'true'; closeEditingPopovers(); closeMenu();
    setPopover($('#cursor-controls'), opening); cursorButton.setAttribute('aria-expanded', String(opening));
  },
  'toggle-select'() {
    state.selecting = !state.selecting;
    cursorButton.classList.toggle('selecting', state.selecting);
    $('#select-button').setAttribute('aria-pressed', String(state.selecting));
    $('#select-button').setAttribute('aria-label', state.selecting ? '退出选择' : '选择文字');
    scrambleSelectionLabel(state.selecting ? '退出选择' : '选择文字');
  },
  discard() {
    const cloud = window.SemiNativeEditor?.cloud;
    const detail = window.SemiNativeEditor?.hasCloudDraft() ? '本机及云端草稿都会删除。' : '草稿内容将清空。';
    openDialog('舍弃草稿？', `<p class="dialog-copy">${detail}</p>`, '舍弃', () => {
      if (cloud) return window.SemiNativeEditor.discard();
      resetDraft(); notify('草稿已舍弃'); return true;
    });
    $('#dialog-actions .primary').classList.add('destructive');
  },
  send() {
    if (window.SemiNativeEditor?.handleSend()) return;
    if (state.preview) return notify('离线模式无法发布');
    if (validateArticle()) { showPreview(); notify('进入预览'); }
  },
  'close-dialog'() { dialog.close(); },
  'pick-cover'() { $('#cover-picker').click(); },
  'remove-cover'() {
    coverLoad++; state.coverUrl = ''; $('#cover-upload').disabled = false; renderCover(); changed();
  },
};

document.addEventListener('click', (event) => {
  const target = event.target.closest('button');
  if (event.target.closest('#island') && !event.target.closest('input, textarea')) preserveToolbarKeyboard();
  if (!event.target.closest('.editing-control-group, .editing-popover')) closeEditingPopovers();
  if (!target) return;
  if (target.dataset.drawerPage !== undefined) goToDrawerPage(Number(target.dataset.drawerPage));
  else if (target.dataset.action) actions[target.dataset.action]?.();
  else if (target.dataset.tool) runTool(target.dataset.tool, target.classList.contains('tool-cell'), target);
  else if (target.dataset.contentAction) contentAction(target.dataset.contentAction);
  else if (target.dataset.move) {
    if (suppressCursorClick === target) suppressCursorClick = null;
    else SemiEditor.moveCursor(editor, target.dataset.move, state.selecting);
  }
});
document.addEventListener('pointerdown', (event) => {
  const input = event.target.closest('input, textarea, [contenteditable="true"]');
  if (input && toolbarInputModes.has(input)) {
    const mode = toolbarInputModes.get(input);
    if (mode === null) input.removeAttribute('inputmode');
    else input.setAttribute('inputmode', mode);
    toolbarInputModes.delete(input);
  }
  if (event.target.closest('#topic-title, .tiptap') && state.expanded) setExpanded(false);
  if (event.target.closest('#island, .context-popover, #table-handles') && !event.target.closest('input, textarea')) {
    preserveToolbarKeyboard();
    event.preventDefault();
  }
}, true);
$('#menu-dismiss').addEventListener('click', closeMenu);
$('#dialog-form').addEventListener('submit', (event) => {
  event.preventDefault();
  if (dialogSubmit?.(new FormData(event.target)) !== false) dialog.close();
});
dialog.addEventListener('click', (event) => {
  if (event.target !== dialog) return;
  const rect = dialog.getBoundingClientRect();
  if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
});
titleInput.addEventListener('input', changed);
summaryInput.addEventListener('input', () => { updateSummaryCount(); changed(); });
document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && !dialog.open) {
    closeMenu(); closeEditingPopovers();
    if (state.expanded) setExpanded(false); else if (state.preview) closePreview();
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault(); saveDraft(); notify('草稿已保存到本机');
  }
});
drawerPages.addEventListener('scroll', () => {
  if (drawerPages.clientWidth) updateDrawerPage(Math.round(drawerPages.scrollLeft / drawerPages.clientWidth));
}, { passive: true });
$('#page-indicator').addEventListener('keydown', (event) => {
  const direction = { ArrowLeft: -1, ArrowRight: 1 }[event.key];
  if (!direction) return;
  event.preventDefault();
  const page = Math.max(0, Math.min(1, state.page + direction));
  goToDrawerPage(page); $(`[data-drawer-page="${page}"]`).focus();
});
let drawerDrag = null, drawerDragged = false;
drawerPages.addEventListener('pointerdown', (event) => {
  drawerDragged = false;
  if (event.pointerType !== 'mouse' || event.button !== 0 || !state.expanded || event.target.closest('textarea, input')) return;
  drawerDrag = {
    x: event.clientX, y: event.clientY, distance: 0,
    scrollLeft: drawerPages.scrollLeft, page: state.page,
    scale: drawerPages.getBoundingClientRect().width / drawerPages.clientWidth,
  };
});
drawerPages.addEventListener('pointermove', (event) => {
  if (!drawerDrag) return;
  const dx = (event.clientX - drawerDrag.x) / drawerDrag.scale;
  const dy = (event.clientY - drawerDrag.y) / drawerDrag.scale;
  if (!drawerDragged) {
    if (Math.abs(dy) > 8 && Math.abs(dy) > Math.abs(dx)) { drawerDrag = null; return; }
    if (Math.abs(dx) < 8) return;
    drawerDragged = true; drawerPages.classList.add('dragging'); drawerPages.setPointerCapture(event.pointerId);
  }
  drawerDrag.distance = -dx;
  drawerPages.scrollLeft = drawerDrag.scrollLeft - dx;
});
function endDrawerDrag(cancelled = false) {
  const drag = drawerDrag; drawerDrag = null; drawerPages.classList.remove('dragging');
  if (!drag || !drawerDragged) return;
  const page = !cancelled && Math.abs(drag.distance) >= 48 ? drag.page + Math.sign(drag.distance) : drag.page;
  goToDrawerPage(page);
}
drawerPages.addEventListener('pointerup', () => endDrawerDrag());
drawerPages.addEventListener('pointercancel', () => { endDrawerDrag(true); drawerDragged = false; });
drawerPages.addEventListener('lostpointercapture', () => endDrawerDrag(true));
drawerPages.addEventListener('pointerleave', () => { if (!drawerDragged) drawerDrag = null; });
drawerPages.addEventListener('click', (event) => {
  if (!drawerDragged) return;
  event.preventDefault(); event.stopPropagation(); drawerDragged = false;
}, true);

const quickTools = $('#quick-tools');
let quickDrag = null, quickDragged = false;
quickTools.addEventListener('pointerdown', (event) => {
  quickDragged = false;
  if (event.pointerType !== 'mouse' || event.button !== 0 || quickTools.scrollWidth <= quickTools.clientWidth) return;
  quickDrag = { x: event.clientX, scrollLeft: quickTools.scrollLeft, scale: quickTools.getBoundingClientRect().width / quickTools.clientWidth };
});
quickTools.addEventListener('pointermove', (event) => {
  if (!quickDrag) return;
  const dx = (event.clientX - quickDrag.x) / quickDrag.scale;
  if (!quickDragged && Math.abs(dx) < 6) return;
  if (!quickDragged) {
    quickDragged = true;
    quickTools.setPointerCapture(event.pointerId);
    quickTools.classList.add('dragging');
  }
  quickTools.scrollLeft = quickDrag.scrollLeft - dx;
});
function endQuickDrag() {
  quickDrag = null;
  quickTools.classList.remove('dragging');
}
quickTools.addEventListener('pointerup', endQuickDrag);
quickTools.addEventListener('pointercancel', () => { endQuickDrag(); quickDragged = false; });
quickTools.addEventListener('lostpointercapture', endQuickDrag);
quickTools.addEventListener('pointerleave', () => { if (!quickDragged) endQuickDrag(); });
// A swipe scrolls the strip without applying the tool under the pointer.
quickTools.addEventListener('click', (event) => {
  if (!quickDragged) return;
  event.preventDefault(); event.stopPropagation(); quickDragged = false;
}, true);

let dragStart = null, grabberDragged = false;
$('#grabber').addEventListener('pointerdown', (event) => {
  if (phone.classList.contains('keyboard-open')) return;
  dragStart = event.clientY; grabberDragged = false; $('#grabber').setPointerCapture(event.pointerId);
});
$('#grabber').addEventListener('pointerup', (event) => {
  if (dragStart == null) return;
  const delta = event.clientY - dragStart; dragStart = null;
  if (Math.abs(delta) > 24) { event.preventDefault(); grabberDragged = true; setExpanded(delta < 0); }
});
$('#grabber').addEventListener('click', (event) => {
  if (grabberDragged) { event.stopPropagation(); grabberDragged = false; }
});
let cursorOrigin = null, cursorDragged = false;
cursorButton.addEventListener('pointerdown', (event) => {
  cursorOrigin = { x: event.clientX, y: event.clientY }; cursorDragged = false;
  cursorButton.setPointerCapture(event.pointerId);
});
cursorButton.addEventListener('pointermove', (event) => {
  if (!cursorOrigin) return;
  const dx = event.clientX - cursorOrigin.x, dy = event.clientY - cursorOrigin.y;
  if (Math.abs(dx) + Math.abs(dy) > 6) cursorDragged = true;
  const xSteps = Math.trunc(dx / 12), ySteps = Math.trunc(dy / 28);
  for (let index = 0; index < Math.abs(xSteps); index++) SemiEditor.moveCursor(editor, xSteps < 0 ? 'left' : 'right', state.selecting);
  for (let index = 0; index < Math.abs(ySteps); index++) SemiEditor.moveCursor(editor, ySteps < 0 ? 'up' : 'down', state.selecting);
  cursorOrigin.x += xSteps * 12; cursorOrigin.y += ySteps * 28;
});
cursorButton.addEventListener('pointerup', () => { cursorOrigin = null; });
cursorButton.addEventListener('pointercancel', () => { cursorOrigin = null; cursorDragged = false; });
cursorButton.addEventListener('click', (event) => {
  if (cursorDragged) { event.stopPropagation(); cursorDragged = false; }
});
cursorButton.addEventListener('keydown', (event) => {
  const direction = { ArrowLeft: 'left', ArrowRight: 'right', ArrowUp: 'up', ArrowDown: 'down' }[event.key];
  if (direction) { event.preventDefault(); SemiEditor.moveCursor(editor, direction, event.shiftKey || state.selecting); }
});
function stopCursorRepeat() {
  if (cursorRepeat) clearTimeout(cursorRepeat.timer);
  cursorRepeat = null;
}
$('#cursor-controls').addEventListener('pointerdown', event => {
  const button = event.target.closest('[data-move]');
  if (!button || event.button !== 0) return;
  event.preventDefault(); stopCursorRepeat(); suppressCursorClick = null;
  button.setPointerCapture(event.pointerId);
  const started = performance.now();
  cursorRepeat = { button, timer: setTimeout(function repeat() {
    if (!cursorRepeat || $('#cursor-controls').inert) return stopCursorRepeat();
    suppressCursorClick = button;
    SemiEditor.moveCursor(editor, button.dataset.move, state.selecting);
    cursorRepeat.timer = setTimeout(repeat, performance.now() - started > 900 ? 28 : 55);
  }, 280) };
});
for (const name of ['pointerup', 'pointercancel', 'lostpointercapture']) {
  $('#cursor-controls').addEventListener(name, stopCursorRepeat);
}
window.addEventListener('blur', stopCursorRepeat);
// Keyboard-up gestures stay on the toolbar; they must not pan the WebView behind it.
island.addEventListener('touchmove', event => {
  if (phone.classList.contains('keyboard-open')) event.preventDefault();
}, { passive: false });
island.addEventListener('wheel', event => {
  if (phone.classList.contains('keyboard-open')) event.preventDefault();
}, { passive: false });
$('#image-picker').addEventListener('change', async (event) => {
  const file = event.target.files[0]; if (!file) return;
  event.target.value = '';
  try {
    const url = await readLocalImage(file);
    if (window.SemiNativeEditor?.cloud) {
      window.SemiNativeEditor.uploadCloud('body', url, file.name);
      return;
    }
    editingChain().setImage({ src: url, alt: file.name }).run();
    notify('图片已加入，仅保存在本机');
  } catch { notify(file.size >= 5 * 1024 * 1024 ? '图片需小于 5 MB' : '图片无法读取'); }
});
$('#cover-picker').addEventListener('change', async (event) => {
  const file = event.target.files[0]; if (!file) return;
  event.target.value = '';
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) { notify('支持 JPG、PNG、WebP'); return; }
  const load = ++coverLoad;
  $('#cover-upload').disabled = true; $('#cover-upload-label').textContent = '处理中';
  try {
    const url = await readLocalImage(file);
    if (load !== coverLoad) return;
    if (window.SemiNativeEditor?.cloud) {
      window.SemiNativeEditor.uploadCloud('cover', url, '', load);
      return;
    }
    state.coverUrl = url; changed();
  } catch {
    if (load === coverLoad) notify(file.size >= 5 * 1024 * 1024 ? '图片需小于 5 MB' : '图片无法读取');
  } finally {
    if (load === coverLoad) { $('#cover-upload').disabled = false; renderCover(); }
  }
});
window.addEventListener('pagehide', () => { if (!state.saved) saveDraft(); });
function fitPhone() {
  const scale = Math.min(1, (innerHeight - 36) / 922, (innerWidth - 36) / 414);
  phone.style.setProperty('--phone-scale', String(Math.max(.2, scale)));
  goToDrawerPage(state.page, false);
}
window.addEventListener('resize', fitPhone);
hydrateIcons(); restoreDraft(); mountEditor(); renderTools(); updateCounts(); fitPhone();
const sling = PublishSling.bind($('#publish-control'), reducePreviewMotion, () => window.SemiNativeEditor?.reportMotion());
$('#preview-avatar-image').addEventListener('load', event => { event.target.hidden = false; });
$('#preview-avatar-image').addEventListener('error', event => { event.target.hidden = true; });
const initialState = new URLSearchParams(location.search).get('state');
if (initialState === 'tools') setExpanded(true);
if (initialState === 'menu') actions.menu();
if (initialState === 'preview') showPreview();
