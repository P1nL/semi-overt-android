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
  if (state.preview) return closePreview();
  if (state.expanded) return setExpanded(false);
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
let cloudLabel = '草稿自动保存到本机';
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
  cloudLabel = message;
  $('#save-label').textContent = message;
}
function updateCloudButtons() {
  if (!cloudMode) return;
  $('#cloud-save').hidden = cloudBlocked || cloudLocked;
  $('#cloud-reload').hidden = !cloudBlocked;
  $('#cloud-copy').hidden = !cloudBlocked;
  $('#cloud-export').hidden = false;
  $('button[data-action="discard"]').hidden = Boolean(cloudMetadata.remoteId) || cloudBlocked || cloudLocked;
  const send = $('.send-button');
  send.disabled = cloudBusy || (cloudLocked && cloudMetadata.status !== 'PENDING') || (cloudBlocked && state.preview);
  send.setAttribute('aria-label', cloudMetadata.status === 'PENDING' ? '撤回审核'
    : state.preview ? '提交审核' : '检查并预览文章');
  const label = cloudMetadata.status === 'PENDING' ? '待审核'
    : cloudMetadata.status === 'APPROVED' ? '已发布'
    : cloudMetadata.status === 'REJECTED' ? '未通过' : '预览';
  $('#preview-label').textContent = label;
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
  if (!cloudBlocked && !cloudBusy) statusMessage('本机草稿 · 待同步');
  scheduleCloud();
}
function sendCloud(action) {
  if (!cloudReady || cloudBusy || uploads.size) return;
  if (action === 'auto' && !cloudMetadata.dirty) return;
  const maySaveEmpty = cloudMetadata.remoteId && (action === 'auto' || action === 'save');
  if (!maySaveEmpty && action !== 'reload' && action !== 'cancel' && !Object.values(fields()).some(value => value.trim())) {
    if (action !== 'auto') notify('请输入草稿内容');
    return;
  }
  clearTimeout(cloudTimer);
  saveDraft();
  cloudBusy = true;
  statusMessage(action === 'submit' ? '正在提交审核…' : action === 'cancel' ? '正在撤回…' : '正在与云端同步…');
  updateCloudButtons();
  SemiNative.cloudSave(draftKey, action, JSON.stringify(fields()));
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
  if (locked && !state.preview) showPreview();
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
  } else if (document && keepLocal && !conflict) copyMetadata(document);
  if (!locked && state.preview) closePreview();
  setCloudLocked(locked);
  saveDraft();
  statusMessage(message);
  updateCloudButtons();
  if (cloudMetadata.reason) notify(cloudMetadata.reason);
  if (conflict || (!document && message !== '草稿自动保存到本机')) notify(message);
  if (keepLocal && !conflict) scheduleCloud();
}
function cloudSaved(document, payload, message, announce) {
  const latest = fields();
  const sent = JSON.parse(payload);
  copyMetadata(document);
  cloudMetadata.dirty = Object.keys(latest).some(field => latest[field] !== sent[field]);
  cloudBusy = false; cloudBlocked = false;
  setCloudLocked(document.status !== 'DRAFT' && document.status !== 'RETURNED');
  saveDraft();
  statusMessage(cloudMetadata.dirty ? '本机草稿 · 待同步' : message);
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
  if (cloudMetadata.status === 'PENDING') {
    if (cloudBlocked) { notify('请先处理本机修改'); return true; }
    openDialog('撤回审核？', '<p class="dialog-copy">撤回后草稿可继续编辑。</p>', '撤回审核', () => {
      sendCloud('cancel'); return true;
    });
    return true;
  }
  if (cloudLocked) return true;
  if (!state.preview) {
    if (!titleInput.value.trim()) { notify('请输入标题'); return true; }
    if (body.length < 50) { notify('正文至少需要 50 个字符'); return true; }
    showPreview();
    return true;
  }
  if (cloudBlocked) { notify('请先处理云端版本冲突'); return true; }
  openDialog('提交审核？', '<p class="dialog-copy">审核通过后，文章将公开显示。</p>', '提交审核', () => {
    sendCloud('submit'); return true;
  });
  return true;
}
if (cloudMode) {
  actions['cloud-save'] = () => { closeMenu(); sendCloud('save'); };
  actions['cloud-reload'] = () => openDialog('重新读取云端？',
    '<p class="dialog-copy">读取成功后将替换本机内容。可先导出 Markdown 或另存草稿。</p>', '读取云端', () => {
      sendCloud('reload'); return true;
    });
  actions['cloud-copy'] = () => { closeMenu(); sendCloud('copy'); };
  actions['cloud-export'] = () => { closeMenu(); downloadDraft(); };
  const offlinePreview = actions.preview;
  actions.preview = () => state.preview ? closeMenu() : offlinePreview();
  const back = actions.back;
  actions.back = () => {
    if (cloudLocked && state.preview) {
      if (cloudBusy) return notify('请等待同步完成');
      saveDraft(); SemiNative.close(); return;
    }
    back();
  };
  $('#back-button').setAttribute('aria-label', '返回草稿箱');
}

window.SemiNativeEditor = {
  cloud: cloudMode,
  persistCloud,
  cloudChanged,
  updateCloudButtons,
  handleSend,
  localDocument: () => JSON.stringify({ ...fields(), ...cloudMetadata }),
  openCloud,
  cloudLinked(document) { copyMetadata(document); persistCloud(); },
  cloudSaved,
  cloudFailed,
  uploadCloud(type, data, alt, coverTicket) {
    if (cloudLocked || cloudBusy) return notify('请等待当前操作完成');
    const id = ++nextUpload;
    uploads.set(id, { type, alt, coverTicket });
    if (type === 'cover') statusMessage('正在上传封面…');
    else notify('正在上传图片…');
    SemiNative.cloudImage(draftKey, String(id), type, data);
  },
  cloudImage(id, url, error) {
    const task = uploads.get(id);
    if (!task) return;
    uploads.delete(id);
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
    clearTimeout(cloudTimer);
    clearTimeout(editorMotionTimer);
    editorMotionTimer = 0;
    stopCursorRepeat();
    if (dialog.open) dialog.close();
    closeMenu();
    closeEditingPopovers();
    window.EditorContextTools?.close();
    if (state.preview && !cloudLocked) closePreview();
    if (state.expanded) setExpanded(false);
    editor.commands.blur();
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
    this.setKeyboardInset(0);
    saveDraft();
  },
};

updateCloudButtons();
document.addEventListener('visibilitychange', () => {
  if (document.hidden && !state.saved) saveDraft();
});
requestAnimationFrame(() => requestAnimationFrame(() => SemiNative.ready(draftKey)));
