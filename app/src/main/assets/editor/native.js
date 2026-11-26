'use strict';

let editorMotionTimer = 0;
let lastMotionReport = -Infinity;

function reportEditorMotion() {
  const now = performance.now();
  if (document.hidden || now - lastMotionReport < 100) return;
  lastMotionReport = now;
  SemiNative.motion();
}

// Inner DOM scrolling and CSS/Web Animations do not use Compose's frame clock.
// Observe them only while active; the decorative background has no such bridge.
function watchEditorMotion() {
  reportEditorMotion();
  if (editorMotionTimer || document.hidden) return;
  editorMotionTimer = setTimeout(function sample() {
    editorMotionTimer = 0;
    if (document.hidden) return;
    reportEditorMotion();
    if (document.getAnimations().some(animation => animation.playState === 'running')) {
      editorMotionTimer = setTimeout(sample, 100);
    }
  }, 0);
}

document.addEventListener('scroll', reportEditorMotion, { capture: true, passive: true });
for (const type of ['pointerdown', 'pointerup', 'click', 'keydown', 'input', 'scrollend',
  'transitionrun', 'transitionend', 'transitioncancel', 'animationstart', 'animationend', 'animationcancel']) {
  document.addEventListener(type, watchEditorMotion, { capture: true, passive: true });
}

actions.back = () => {
  if (cloudMode && (cloudBusy || uploads.size)) return notify('请等待同步完成');
  if (state.preview && !cloudLocked) return closePreview();
  if (!state.preview && state.expanded) return setExpanded(false);
  saveDraft();
  SemiNative.close();
};

const cloudMode = new URLSearchParams(location.search).get('cloud') === '1';
const emptyMetadata = () => ({ remoteId: null, remoteVersion: null, status: 'DRAFT', reason: null, dirty: false, updatedAt: '' });
let cloudMetadata = emptyMetadata();
if (cloudMode) {
  try {
    const stored = JSON.parse(localStorage.getItem(draftKey) || '{}');
    for (const field of Object.keys(cloudMetadata)) {
      if (stored[field] !== undefined) cloudMetadata[field] = stored[field];
    }
  } catch {}
}
let cloudReady = false;
let cloudBusy = false;
let cloudLocked = false;
let cloudBlocked = false;
let cloudTimer = 0;
let nextUpload = 0;
const uploads = new Map();

function fields() {
  return { title: titleInput.value, body, summary: summaryInput.value, coverUrl: state.coverUrl };
}
function persistCloud() {
  const content = fields();
  if (!cloudMetadata.remoteId && !Object.values(content).some(value => value.trim())) {
    localStorage.removeItem(draftKey);
  } else localStorage.setItem(draftKey, JSON.stringify({ ...content, ...cloudMetadata }));
}
function statusMessage(message) {
  $('#save-label').textContent = message;
}
function updateCloudButtons() {
  const send = $('.send-button');
  send.setAttribute('aria-label', !state.preview ? '检查并预览文章' : cloudMode ? '发布并提交审核' : '离线模式无法发布');
  if (!cloudMode) return;
  const control = $('#publish-control');
  control.classList.toggle('sling', state.preview);
  control.toggleAttribute('data-locked', cloudLocked);
  send.setAttribute('aria-busy', String(state.preview && cloudBusy));
  send.disabled = !cloudReady || cloudLocked || uploads.size > 0 || (state.preview && (cloudBusy || cloudBlocked));
  const discard = $('button[data-action="discard"]');
  discard.hidden = cloudLocked;
  discard.disabled = !cloudReady || cloudBusy || uploads.size > 0 || (cloudBlocked && Boolean(cloudMetadata.remoteId));
  $('#cloud-recovery').hidden = !cloudBlocked;
  $('#cloud-recovery').disabled = cloudBusy;
  const status = $('#review-status');
  status.textContent = { PENDING: '待审核', APPROVED: '已发布', REJECTED: '未通过' }[cloudMetadata.status] || '';
  status.hidden = !status.textContent;
  $('#cancel-review').hidden = cloudMetadata.status !== 'PENDING';
  $('#cancel-review').disabled = cloudBusy || cloudBlocked;
  if (cloudLocked) $('#back-button').setAttribute('aria-label', '返回草稿箱');
}
function scheduleCloud() {
  clearTimeout(cloudTimer);
  if (cloudReady && !cloudBusy && !cloudLocked && !cloudBlocked && !uploads.size && cloudMetadata.dirty) {
    cloudTimer = setTimeout(() => sendCloud('auto'), 2400);
  }
}
function cloudChanged() {
  if (!cloudMode) return;
  cloudMetadata.dirty = true;
  cloudMetadata.updatedAt = new Date().toISOString();
  if (!cloudBlocked && !cloudBusy) statusMessage('待同步');
  scheduleCloud();
}
function sendCloud(action) {
  if (!cloudReady || cloudBusy || uploads.size) return false;
  if (action === 'auto' && !cloudMetadata.dirty) return false;
  const controlAction = ['reload', 'cancel', 'discard'].includes(action);
  const mayBeEmpty = (action === 'auto' && cloudMetadata.remoteId) || controlAction;
  if (!mayBeEmpty && !Object.values(fields()).some(value => value.trim())) {
    if (action !== 'auto') notify('请输入草稿内容');
    return false;
  }
  clearTimeout(cloudTimer);
  saveDraft();
  cloudBusy = true;
  if (action === 'submit') { sling.launch(); notify('提交中'); }
  statusMessage({ submit: '提交中', cancel: '撤回中', discard: '舍弃中' }[action] || '同步中');
  updateCloudButtons();
  SemiNative.cloudSave(draftKey, action, JSON.stringify(controlAction ? {} : fields()));
  return true;
}
function copyMetadata(document) {
  const previousDirty = cloudMetadata.dirty;
  const previousTime = cloudMetadata.updatedAt;
  for (const field of ['remoteId', 'remoteVersion', 'status', 'reason', 'updatedAt']) cloudMetadata[field] = document[field];
  cloudMetadata.dirty = previousDirty;
  if (previousDirty) cloudMetadata.updatedAt = previousTime;
}
function setCloudLocked(locked) {
  cloudLocked = locked;
  titleInput.readOnly = summaryInput.readOnly = locked;
  editor.setEditable(!locked, false);
  if (locked) {
    if (!state.preview) showPreview(false);
    renderPreviewDate(cloudMetadata.updatedAt);
  }
}
function openCloud(document, keepLocal, conflict, message, locked) {
  cloudReady = true;
  cloudBusy = false;
  cloudBlocked = conflict;
  if (document && !keepLocal) {
    const bodyChanged = body !== document.body;
    titleInput.value = document.title; body = document.body;
    summaryInput.value = document.summary; state.coverUrl = document.coverUrl;
    copyMetadata(document);
    cloudMetadata.dirty = false;
    cloudMetadata.updatedAt = document.updatedAt;
    if (bodyChanged) mountEditor();
    updateSummaryCount(); renderCover(); updateCounts();
    if (locked && state.preview) renderPreview();
  } else if (document && keepLocal && !conflict) copyMetadata(document);
  if (!locked && state.preview) closePreview();
  setCloudLocked(locked);
  saveDraft();
  statusMessage(message);
  updateCloudButtons();
  if (cloudMetadata.reason) notify(cloudMetadata.reason);
  if (conflict) notify(message);
  if (keepLocal && !conflict) scheduleCloud();
}
function cloudSaved(document, payload, message, announce) {
  const latest = fields();
  const sent = JSON.parse(payload);
  copyMetadata(document);
  cloudMetadata.dirty = Object.keys(latest).some(field => latest[field] !== sent[field]);
  if (!cloudMetadata.dirty) cloudMetadata.updatedAt = document.updatedAt;
  cloudBusy = false; cloudBlocked = false;
  setCloudLocked(document.status !== 'DRAFT' && document.status !== 'RETURNED');
  saveDraft();
  statusMessage(cloudMetadata.dirty ? '待同步' : message);
  updateCloudButtons();
  if (announce) notify(message);
  scheduleCloud();
}
function cloudFailed(message, conflict) {
  cloudBusy = false;
  cloudBlocked = conflict;
  if (conflict) cloudMetadata.dirty = true;
  saveDraft();
  statusMessage(message);
  updateCloudButtons();
  notify(message);
}
function handleSend() {
  if (!cloudMode) return false;
  if (cloudLocked || !cloudReady || uploads.size) return true;
  if (!state.preview) {
    if (validateArticle()) { showPreview(); notify('进入预览'); }
    return true;
  }
  if (!cloudBlocked && !cloudBusy && validateArticle()) sendCloud('submit');
  return true;
}
if (cloudMode) {
  actions['cloud-recovery'] = () => openDialog('处理同步冲突',
    '<p class="dialog-copy">重新读取云端会替换本机内容。</p><div class="choice-list">' +
    '<button type="button" data-action="cloud-reload">读取云端</button>' +
    '<button type="button" data-action="cloud-copy">另存草稿</button></div>');
  actions['cloud-reload'] = () => { if (sendCloud('reload')) dialog.close(); };
  actions['cloud-copy'] = () => { if (sendCloud('copy')) dialog.close(); };
  actions['cancel-review'] = () => openDialog('撤回审核？', '<p class="dialog-copy">撤回后可继续编辑。</p>',
    '撤回审核', () => sendCloud('cancel'));
  $('#back-button').setAttribute('aria-label', '返回草稿箱');
}

window.SemiNativeEditor = {
  cloud: cloudMode,
  persistCloud,
  cloudChanged,
  hasCloudDraft: () => Boolean(cloudMetadata.remoteId),
  discard: () => sendCloud('discard'),
  previewChanged: updateCloudButtons,
  setPreviewAuthor(author) {
    previewAuthor = author;
    if (state.preview) renderPreviewAuthor();
  },
  setMotionEnabled(enabled) {
    phone.dataset.motion = enabled ? 'on' : 'off';
    if (!enabled) sling.reset();
  },
  reportMotion: reportEditorMotion,
  handleSend,
  localDocument: () => JSON.stringify({ ...fields(), ...cloudMetadata }),
  openCloud,
  cloudLinked(document) { copyMetadata(document); persistCloud(); },
  cloudSaved,
  cloudFailed,
  cloudDiscarded() {
    cloudBusy = cloudBlocked = false;
    cloudMetadata = emptyMetadata();
    resetDraft();
    statusMessage('自动保存');
    updateCloudButtons();
    notify('草稿已舍弃');
  },
  uploadCloud(type, data, alt, coverTicket) {
    if (cloudLocked || cloudBusy) return notify('请等待当前操作完成');
    const id = ++nextUpload;
    uploads.set(id, { type, alt, coverTicket });
    if (type === 'cover') statusMessage('上传封面中');
    else notify('上传图片中');
    updateCloudButtons();
    SemiNative.cloudImage(draftKey, String(id), type, data);
  },
  cloudImage(id, url, error) {
    const task = uploads.get(id);
    if (!task) return;
    uploads.delete(id);
    updateCloudButtons();
    if (error) {
      statusMessage(error); notify(error); scheduleCloud(); return;
    }
    if (task.type === 'body') {
      editingChain().setImage({ src: url, alt: task.alt }).run();
      notify('已插入图片');
    } else if (task.coverTicket === coverLoad) {
      state.coverUrl = url; renderCover(); changed();
    }
    scheduleCloud();
  },
  setTheme: SemiEditorTheme.setTheme,
  setKeyboardInset(pixels) {
    phone.style.setProperty('--keyboard-inset', `${Math.max(0, pixels / devicePixelRatio)}px`);
    phone.classList.toggle('keyboard-open', pixels > 0);
    window.EditorContextTools?.update();
  },
  back() {
    if (dialog.open) return dialog.close();
    if ($('#overflow-menu').dataset.open === 'true') return closeMenu();
    if ($('#editing-actions').dataset.open === 'true' || $('#cursor-controls').dataset.open === 'true') return closeEditingPopovers();
    if (window.EditorContextTools?.back()) return;
    actions.back();
  },
  flush: saveDraft,
  suspend() {
    clearTimeout(editorMotionTimer);
    editorMotionTimer = 0;
    stopCursorRepeat();
    if (dialog.open) dialog.close();
    closeMenu();
    closeEditingPopovers();
    window.EditorContextTools?.close();
    if (state.preview && !cloudLocked) closePreview(false);
    sling.reset();
    if (state.expanded) setExpanded(false);
    editor.commands.blur();
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
    this.setKeyboardInset(0);
    clearTimeout(cloudTimer);
    dismissTip();
    saveDraft();
  },
};

updateCloudButtons();
document.addEventListener('visibilitychange', () => {
  if (document.hidden && !state.saved) saveDraft();
});
requestAnimationFrame(() => requestAnimationFrame(() => SemiNative.ready(draftKey)));
