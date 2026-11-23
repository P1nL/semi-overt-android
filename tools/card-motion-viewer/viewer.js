/* Native screenshots are the animation. This player only changes their playback clock. */
const $ = (selector, root = document) => root.querySelector(selector);
const clips = new Map();
const players = [];
let speed = .5;
let lastTick = null;
let focusedPlayer = null;
let ready = false;
const sync = $('#sync');
const looping = $('#loop');
const status = $('#status');
const playAll = $('#play-all');

function setSpeed(value) {
  if (!Number.isFinite(value) || value < .1 || value > 4) return;
  speed = value;
  $('#speed').value = value;
  document.querySelectorAll('[data-speed]').forEach(button =>
    button.setAttribute('aria-pressed', String(Number(button.dataset.speed) === value)));
}
document.querySelectorAll('[data-speed]').forEach(button =>
  button.addEventListener('click', () => setSpeed(Number(button.dataset.speed))));
$('#speed').addEventListener('change', event => {
  setSpeed(Number(event.target.value));
  event.target.value = speed;
});

async function loadClip(name, progress) {
  if (clips.has(name)) return clips.get(name);
  const task = (async () => {
    const response = await fetch(`assets/${name}/frames.json`);
    if (!response.ok) throw new Error(`片段 ${name} 载入失败（${response.status}）`);
    const clip = await response.json();
    clip.duration = clip.frames.at(-1).t;
    // Decode before playback so seeking and slow motion do not wait for image requests.
    let loaded = 0;
    let cursor = 0;
    const worker = async () => {
      while (cursor < clip.frames.length) {
        const frame = clip.frames[cursor++];
        const image = new Image();
        image.src = `assets/${name}/${frame.file}`;
        await image.decode();
        frame.image = image;
        progress?.(++loaded, clip.frames.length);
      }
    };
    await Promise.all(Array.from({length: 4}, worker));
    return clip;
  })();
  clips.set(name, task);
  return task;
}

function stageText(player, frame) {
  if (player.name === 'home-lift' || player.name === 'home-lowering') {
    if (frame.t === 0) return '起始画面';
    if (player.name === 'home-lowering') return frame.t < 900
      ? '选卡回落 · 900 ms 弹簧路径' : '选卡已回到队列';
    return frame.t < 900 ? '选卡上浮 · 900 ms 弹簧路径' : '选卡已到位 · 头像接续';
  }
  const p = frame.p;
  if (frame.t === 0) return '起始画面';
  if (player.name.endsWith('-closing')) {
    if (p <= 0) return '来源页 · 已完成';
    if (player.name === 'home-closing') {
      const elapsed = (1 - p) * 800;
      if (elapsed < 250) return '正文先退场 · 标题停留';
      if (elapsed < 480) return '正文退场 · 标题匀速回落';
      if (elapsed < 670) return '标题继续回落 · 容器原位淡入';
      return '背景已恢复 · 标题继续归位';
    }
    if (p >= .80) return '正文退场 · 头像淡出';
    if (p >= .55) return '文案回落 · 容器固定尺寸淡入';
    return '整卡等大回落 · 队列归位';
  }
  if (p >= 1) return '阅读页 · 已完成';
  if (player.name === 'home-reading') {
    if (p >= 550 / 730) return '标题已到位 · 正文继续进入';
    if (p >= 250 / 730) return '标题匀速上升 · 正文同步进入';
    return '文案匀速上升 · 容器原地淡出';
  }
  if (p >= .80) return '文案已到位 · 正文缓慢进入';
  if (p >= .55) return '文案减速上升 · 容器固定尺寸淡出';
  return '整卡等大上提 · 页面信息淡出';
}

function updatePhases() {
  const lift = ['home-lift', 'home-lowering'].includes(players[0].name);
  const closing = players.every(player => player.name.endsWith('-closing'));
  const mixed = players.some(player => player.name.endsWith('-closing')) && !closing;
  const labels = closing
    ? [['首页正文先退场', '480 ms · 头像同步淡出'], ['首页标题开始回落', '错峰 250 ms · 用 550 ms 归位'],
       ['分类整卡归位', '文案已归入卡片'], ['首页标题接近归位', '800 ms 完成 · 背景原地恢复']]
    : mixed
    ? [['起始阶段', '按各自动作方向定位'], ['文案交接', '查看文字与源卡的关系'],
       ['背景过渡', '观察容器与背景透明度'], ['末段动作', '查看页面交接']]
    : [['首页标题匀速上升', '550 ms 到位 · 背景原地淡出'], ['首页正文接入', '标题启动 250 ms 后'],
       ['分类文案减速落位', '容器和背景逐渐淡出'], ['首页标题已到位', '正文继续进入至 730 ms']];
  $('#phase-title').textContent = closing ? '观察收起的节奏' : mixed ? '观察动作的节奏' : '观察展开的节奏';
  document.querySelectorAll('[data-progress]').forEach((button, index) => {
    button.disabled = lift;
    $('span', button).textContent = labels[index][0];
    $('small', button).textContent = labels[index][1];
  });
  $('#phase-note').textContent = lift
    ? '首页选卡上浮和取消选卡回落均沿原弹簧路径，用 900 ms 完成。分类页可独立切换展开或收起。'
    : closing
    ? '首页收起共 800 ms：正文先用 480 ms 退场，标题在第 250 ms 开始匀速回落，用 550 ms 归位；容器与背景原地淡入。分类收起为 1100 ms，文案归入卡片后整卡回落。两段均实际触发系统返回采集。'
    : mixed
    ? '当前并排查看展开与收起。阶段按钮分别按各自的进度方向定位；也可将两个片段切为相同方向同步对照。'
    : '首页标题用 550 ms 匀速到位；正文仅错峰 250 ms，以 480 ms 进入，整段在 730 ms 完成。分类仍为 1200 ms：上提到 160 dp 后文案减速上升，容器固定尺寸淡出。同步开启时，阶段按钮将两段定位到同一实际时间。';
}

function makePlayer(id) {
  const root = $(`#${id}`);
  const canvas = $('canvas', root);
  const ctx = canvas.getContext('2d', {alpha: false});
  const seek = $('.seek', root);
  const play = $('[data-action=play]', root);
  const player = {
    root, name: '', clip: null, t: 0, playing: false, painted: -1,
    async load(name) {
      this.playing = false;
      const loading = $('.loading', root);
      loading.hidden = false;
      root.querySelectorAll('button, input').forEach(control => control.disabled = true);
      this.clip = null;
      this.name = name;
      this.clip = await loadClip(name, (count, total) => {
        loading.textContent = `载入原生帧 ${count} / ${total}`;
      });
      canvas.width = this.clip.frames[0].image.naturalWidth;
      canvas.height = this.clip.frames[0].image.naturalHeight;
      $('.phone', root).style.aspectRatio = `${canvas.width} / ${canvas.height}`;
      this.t = 0;
      this.painted = -1;
      seek.max = this.clip.duration;
      loading.hidden = true;
      root.querySelectorAll('button, input').forEach(control => control.disabled = false);
      this.render();
    },
    render() {
      if (!this.clip) return;
      const index = Math.min(this.clip.frames.length - 1, Math.max(0, Math.round(this.t / this.clip.step)));
      const frame = this.clip.frames[index];
      if (this.painted !== index) {
        ctx.drawImage(frame.image, 0, 0, canvas.width, canvas.height);
        this.painted = index;
      }
      seek.value = frame.t;
      $('.clock', root).textContent = `${frame.t} / ${this.clip.duration} ms`;
      $('.stage', root).textContent = stageText(this, frame);
      $('.frame', root).textContent = `帧 ${index} / ${this.clip.frames.length - 1}`;
      play.textContent = this.playing ? '暂停' : '播放';
      play.setAttribute('aria-label', `${id === 'home' ? '首页' : '分类页'}${this.playing ? '暂停' : '播放'}`);
    },
  };
  const focus = () => {focusedPlayer = player;};
  root.addEventListener('pointerdown', focus);
  root.addEventListener('focusin', focus);
  play.addEventListener('click', () => toggle(sync.checked ? players : [player]));
  $('.phone', root).addEventListener('click', () => toggle(sync.checked ? players : [player]));
  $('[data-action=restart]', root).addEventListener('click', () => restart(sync.checked ? players : [player]));
  seek.addEventListener('input', () => {
    const targets = sync.checked ? players : [player];
    targets.forEach(target => {
      target.playing = false;
      target.t = Math.min(Number(seek.value), target.clip.duration);
      target.render();
    });
    updateTransport();
  });
  return player;
}

function updateTransport() {
  playAll.textContent = players.some(player => player.playing) ? '全部暂停' : '同时播放';
}
function toggle(targets) {
  if (!ready) return;
  const shouldPlay = !targets.some(player => player.playing);
  if (shouldPlay && targets.every(player => player.t >= player.clip.duration)) {
    targets.forEach(player => player.t = 0);
  }
  targets.forEach(player => {
    if (shouldPlay && player.t >= player.clip.duration) player.t = 0;
    player.playing = shouldPlay;
    player.render();
  });
  lastTick = null;
  updateTransport();
}
function restart(targets) {
  if (!ready) return;
  targets.forEach(player => {player.t = 0; player.playing = true; player.render();});
  lastTick = null;
  updateTransport();
}
function step(direction) {
  if (!ready) return;
  const targets = sync.checked ? players : [focusedPlayer || players[0]];
  targets.forEach(player => {
    player.playing = false;
    player.t = Math.max(0, Math.min(player.clip.duration, (Math.round(player.t / 16) + direction) * 16));
    player.render();
  });
  updateTransport();
}
function tick(timestamp) {
  const delta = lastTick === null ? 0 : timestamp - lastTick;
  lastTick = timestamp;
  if (sync.checked && ready && players.some(player => player.playing)) {
    const duration = Math.max(...players.map(player => player.clip.duration));
    let time = Math.max(...players.map(player => player.t)) + delta * speed;
    const ended = time >= duration && !looping.checked;
    if (looping.checked) time %= duration;
    players.forEach(player => {
      player.t = Math.min(time, player.clip.duration);
      player.playing = !ended;
      player.render();
    });
  } else {
    players.forEach(player => {
      if (!player.playing || !player.clip) return;
      player.t += delta * speed;
      if (player.t >= player.clip.duration) {
        if (looping.checked) player.t %= player.clip.duration;
        else {player.t = player.clip.duration; player.playing = false;}
      }
      player.render();
    });
  }
  updateTransport();
  requestAnimationFrame(tick);
}

$('#play-all').addEventListener('click', () => toggle(players));
$('#restart-all').addEventListener('click', () => restart(players));
$('#prev').addEventListener('click', () => step(-1));
$('#next').addEventListener('click', () => step(1));
sync.addEventListener('change', () => {
  if (!ready || !sync.checked) return;
  const time = (focusedPlayer || players[0]).t;
  players.forEach(player => {player.playing = false; player.t = Math.min(time, player.clip.duration); player.render();});
  updateTransport();
});
document.addEventListener('keydown', event => {
  if (['INPUT', 'SELECT', 'BUTTON', 'TEXTAREA'].includes(event.target.tagName)) return;
  if (event.code === 'Space') {event.preventDefault(); toggle(players);}
  if (event.key === 'ArrowLeft') {event.preventDefault(); step(-1);}
  if (event.key === 'ArrowRight') {event.preventDefault(); step(1);}
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden) {
    players.forEach(player => {player.playing = false; player.render();});
    updateTransport();
  }
  lastTick = null;
});
$('#home-clip').addEventListener('change', async event => {
  ready = false;
  event.target.disabled = true;
  $('#category-clip').disabled = true;
  players.forEach(player => {player.playing = false; player.render();});
  try {
    await players[0].load(event.target.value);
    players[1].t = 0;
    players[1].render();
    ready = true;
    event.target.disabled = false;
    $('#category-clip').disabled = false;
    updatePhases();
    status.textContent = '原生画面已就绪 · 点击画面也可播放或暂停';
  } catch (error) {showError(error);}
  updateTransport();
});
$('#category-clip').addEventListener('change', async event => {
  ready = false;
  event.target.disabled = true;
  $('#home-clip').disabled = true;
  players.forEach(player => {player.playing = false; player.render();});
  try {
    await players[1].load(event.target.value);
    if (event.target.value === 'category-drag') sync.checked = false;
    ready = true;
    event.target.disabled = false;
    $('#home-clip').disabled = false;
    updatePhases();
    status.textContent = event.target.value === 'category-drag'
      ? '分类片段从 160 dp 上提边界开始；手指仍按住时，文案已自动接续减速上升'
      : '原生画面已就绪 · 点击画面也可播放或暂停';
  } catch (error) {showError(error);}
  updateTransport();
});
document.querySelectorAll('[data-progress]').forEach((button, index) => {
  button.addEventListener('click', () => {
    if (!ready) return;
    const targets = players.map(player => {
      const closing = player.name.endsWith('-closing');
      const homeThreshold = player.name === 'home-reading'
        ? (index === 1 ? 250 / 730 : index === 3 ? 550 / 730 : null) : null;
      const closeThreshold = player.name === 'home-closing' && index === 1 ? 1 - 250 / 800 : button.dataset.closingProgress;
      const threshold = Number(closing ? closeThreshold : homeThreshold ?? button.dataset.progress);
      return player.clip.frames.find(frame => closing ? frame.p <= threshold : frame.p >= threshold)?.t ?? player.t;
    });
    const anchor = index === 2 ? 1 : 0;
    players.forEach((player, playerIndex) => {
      player.playing = false;
      player.t = Math.min(sync.checked ? targets[anchor] : targets[playerIndex], player.clip.duration);
      player.render();
    });
    updateTransport();
  });
});
function showError(error) {
  status.textContent = `${error.message}。请用本地服务打开预览，并检查 assets 目录。`;
  status.classList.add('error');
  players.forEach(player => {
    const label = $('.loading', player.root);
    label.hidden = false;
    label.textContent = '原生帧载入失败';
    player.root.querySelectorAll('button, input').forEach(control => control.disabled = true);
  });
  document.querySelectorAll('.transport button, [data-progress]').forEach(button => button.disabled = true);
}

players.push(makePlayer('home'), makePlayer('category'));
Promise.all([players[0].load($('#home-clip').value), players[1].load($('#category-clip').value)]).then(() => {
  ready = true;
  $('#home-clip').disabled = false;
  $('#category-clip').disabled = false;
  document.querySelectorAll('.transport button').forEach(button => button.disabled = false);
  updatePhases();
  status.textContent = '原生画面已就绪 · 点击画面也可播放或暂停';
}).catch(showError);
requestAnimationFrame(tick);
