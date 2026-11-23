import { Geometry, Mesh, Program, Renderer, Transform } from 'ogl';
import { strokeAttributes, strokeVertexShader } from './stroke.js';
import {
  INITIAL_ANIMATION_DELAY_MS, LINE_GAP_PX, POINT_GAP_PX,
  lineCurveVertexShader, gridPointVertexShader, borderVertexShader,
  createLineCurveData, createPointData,
} from '@semi/web-background-source';

const MAX_DEVICE_PIXEL_RATIO = 1.8;
const BACKGROUND_FRAME_INTERVAL_MS = 1000 / 30;
const FRAME_TIMESTAMP_TOLERANCE_MS = 1;
const FRAME_REQUEST_LEAD_MS = 6;
const STROKE_WIDTH_PX = 2.3;
const POINT_DIAMETER_PX = 4;
const STAR_SPAN_PX = 8;
const STAR_INK = 0xffdd13;
const EDGE_FEATHER_RADIUS_PX = 0.5;
const environment = { active: false, motionEnabled: true };
const ink = Number(new URLSearchParams(location.search).get('ink') ?? 0xeb641b);
const inkShader = `
precision highp float;
uniform vec3 uInk;
`;
const lineFragmentShader = `${inkShader}
uniform float uStrokeHalfWidth;
varying float vStrokeOffset;
varying float vStrokeFeather;

void main() {
  float coverage = 1.0 - smoothstep(uStrokeHalfWidth - vStrokeFeather,
    uStrokeHalfWidth + vStrokeFeather, abs(vStrokeOffset));
  gl_FragColor = vec4(uInk * coverage, coverage);
}
`;
const pointVertexShader = gridPointVertexShader
  .replace('void main() {', `uniform float uStarSize;
varying float vPointInterior;
varying float vPointSize;

void main() {`)
  .replace('gl_PointSize = uPointSize;', `vPointInterior = interior;
  vPointSize = mix(uPointSize, uStarSize, interior);
  gl_PointSize = vPointSize;`);
const pointFragmentShader = `${inkShader}
uniform vec3 uStarInk;
varying float vPointInterior;
varying float vPointSize;

float fourPointStarDistance(vec2 offset, float radius) {
  vec2 quadrant = abs(offset);
  vec2 folded = vec2(max(quadrant.x, quadrant.y), min(quadrant.x, quadrant.y));
  vec2 tip = vec2(radius, 0.0);
  vec2 side = vec2(radius / 3.0) - tip;
  vec2 fromTip = folded - tip;
  float projection = clamp(dot(fromTip, side) / dot(side, side), 0.0, 1.0);
  return length(fromTip - projection * side) * sign(side.y * fromTip.x - side.x * fromTip.y);
}

void main() {
  float outerRadius = vPointSize * 0.5;
  vec2 offset = gl_PointCoord - vec2(0.5);
  float coverage;
  if (vPointInterior > 0.5) {
    float distanceToEdge = fourPointStarDistance(offset * vPointSize, outerRadius - ${EDGE_FEATHER_RADIUS_PX});
    coverage = 1.0 - smoothstep(-${EDGE_FEATHER_RADIUS_PX}, ${EDGE_FEATHER_RADIUS_PX}, distanceToEdge);
  } else {
    float distanceToCenter = length(offset) * vPointSize;
    coverage = 1.0 - smoothstep(outerRadius - 2.0 * ${EDGE_FEATHER_RADIUS_PX}, outerRadius, distanceToCenter);
  }
  gl_FragColor = vec4(mix(uInk, uStarInk, vPointInterior) * coverage, coverage);
}
`;

function rgbComponents(color) {
  return [(color >> 16 & 255) / 255, (color >> 8 & 255) / 255, (color & 255) / 255];
}

const uniforms = {
  uInk: { value: rgbComponents(ink) },
  uStarInk: { value: rgbComponents(STAR_INK) },
  uResolution: { value: [1, 1] },
  uArtboard: { value: [0, 0, 1, 1] },
  uGapX: { value: 1 },
  uPointCount: { value: 2 },
  uTime: { value: 0 },
  uPointSize: { value: POINT_DIAMETER_PX + 2 * EDGE_FEATHER_RADIUS_PX },
  uStarSize: { value: STAR_SPAN_PX + 2 * EDGE_FEATHER_RADIUS_PX },
  uStrokeHalfWidth: { value: STROKE_WIDTH_PX * 0.5 },
  uStrokeFeather: { value: EDGE_FEATHER_RADIUS_PX },
  uPointer: { value: [0, 0, 0, 0] },
  uPointerStrength: { value: 0 },
};
const canvas = document.querySelector('canvas');
const recordsBackdrop = document.getElementById('records-backdrop');
let renderer = null;
let scene = null;
let frameId = 0;
let frameTimer = 0;
let resizeFrameId = 0;
let startTimer = 0;
let startDelayFinished = false;
let nextRenderAt = null;
let lastRenderedAt = null;
let elapsedMs = 0;
let ready = false;
let error = null;
const meshes = [];
const programs = [];

function shouldPlay() {
  return ready && startDelayFinished && environment.active && environment.motionEnabled && !document.hidden;
}

function drawFrame() {
  if (!ready) return;
  uniforms.uTime.value = environment.motionEnabled ? elapsedMs * 0.001 : 0;
  renderer.render({ scene, sort: false, frustumCull: false });
}

function requestFrame() {
  frameTimer = 0;
  if (shouldPlay()) frameId = requestAnimationFrame(render);
}

function queueFrame() {
  if (!shouldPlay() || frameId !== 0 || frameTimer !== 0) return;
  // Request a display callback only when the next 30 fps draw is close.
  const wait = nextRenderAt === null ? 0 : nextRenderAt - performance.now() - FRAME_REQUEST_LEAD_MS;
  if (wait > 0) frameTimer = window.setTimeout(requestFrame, wait);
  else requestFrame();
}

function render(now) {
  frameId = 0;
  if (!shouldPlay()) return;
  const scheduledAt = nextRenderAt ?? now;
  const lateness = now - scheduledAt + FRAME_TIMESTAMP_TOLERANCE_MS;
  if (lateness >= 0) {
    const skippedFrames = Math.floor(lateness / BACKGROUND_FRAME_INTERVAL_MS);
    nextRenderAt = scheduledAt + (skippedFrames + 1) * BACKGROUND_FRAME_INTERVAL_MS;
    if (lastRenderedAt !== null) elapsedMs += now - lastRenderedAt;
    lastRenderedAt = now;
    drawFrame();
  }
  queueFrame();
}

function pause() {
  if (frameTimer) clearTimeout(frameTimer);
  if (frameId) cancelAnimationFrame(frameId);
  frameTimer = 0;
  frameId = 0;
  nextRenderAt = null;
  lastRenderedAt = null;
}

function syncPlayback() {
  if (shouldPlay()) queueFrame();
  else {
    pause();
    if (!environment.motionEnabled) drawFrame();
  }
}

function replaceGeometry(mesh, attributes) {
  mesh.geometry.remove();
  mesh.geometry = new Geometry(renderer.gl, attributes);
}

function resize() {
  if (!ready) return;
  const width = window.innerWidth;
  const height = window.innerHeight;
  const dpr = Math.min(window.devicePixelRatio || 1, MAX_DEVICE_PIXEL_RATIO);
  // Fit the actual artboard to the existing native background footprint.
  const artboard = { x: width * 0.05, y: height * 0.10, width: width * 0.90, height: height * 0.80 };
  const lineCount = Math.max(2, Math.floor(artboard.width / LINE_GAP_PX));
  const pointCount = Math.max(2, Math.floor(artboard.height / POINT_GAP_PX));
  renderer.dpr = dpr;
  renderer.setSize(width, height);
  uniforms.uResolution.value = [width, height];
  uniforms.uArtboard.value = [artboard.x, artboard.y, artboard.width, artboard.height];
  uniforms.uGapX.value = artboard.width / Math.max(lineCount - 1, 1);
  uniforms.uPointCount.value = pointCount;
  uniforms.uPointSize.value = Math.max(1.5, POINT_DIAMETER_PX * dpr) + 2 * EDGE_FEATHER_RADIUS_PX;
  uniforms.uStarSize.value = STAR_SPAN_PX * dpr + 2 * EDGE_FEATHER_RADIUS_PX;
  uniforms.uStrokeFeather.value = EDGE_FEATHER_RADIUS_PX / dpr;
  replaceGeometry(meshes[0], strokeAttributes(createLineCurveData(lineCount, pointCount), lineCount, 3));
  replaceGeometry(meshes[1], { gridPoint: { size: 2, data: createPointData(lineCount, pointCount) } });
  drawFrame();
}

function scheduleResize() {
  if (resizeFrameId) return;
  resizeFrameId = requestAnimationFrame(() => { resizeFrameId = 0; resize(); });
}

function dispose() {
  pause();
  clearTimeout(startTimer);
  if (resizeFrameId) cancelAnimationFrame(resizeFrameId);
  window.removeEventListener('resize', scheduleResize);
  document.removeEventListener('visibilitychange', syncPlayback);
  for (const mesh of meshes) mesh.geometry.remove();
  for (const program of programs) program.remove();
  renderer?.gl.getExtension('WEBGL_lose_context')?.loseContext();
  ready = false;
}

window.SemiWebBackground = {
  configure({ blur, blurBackground, ...next }) {
    const playbackChanged = Object.keys(next).some(key => environment[key] !== next[key]);
    Object.assign(environment, next);
    if (playbackChanged) syncPlayback();
    recordsBackdrop.hidden = !blur;
    document.body.style.backgroundColor = blur ? `#${blurBackground.toString(16).padStart(6, '0')}` : '';
    if (blur) {
      const [left, top, width, height] = blur;
      Object.assign(recordsBackdrop.style, { left: `${left}px`, top: `${top}px`, width: `${width}px`, height: `${height}px` });
    }
  },
  getState() {
    return {
      ready, error, playing: frameId !== 0 || frameTimer !== 0, time: uniforms.uTime.value,
      artboard: uniforms.uArtboard.value, viewport: uniforms.uResolution.value,
      pointerStrength: uniforms.uPointerStrength.value, ...environment,
    };
  },
  dispose,
};

try {
  renderer = new Renderer({
    canvas,
    dpr: Math.min(window.devicePixelRatio || 1, MAX_DEVICE_PIXEL_RATIO),
    alpha: true, premultipliedAlpha: true, antialias: true, depth: false, powerPreference: 'high-performance',
  });
  const gl = renderer.gl;
  gl.clearColor(0, 0, 0, 0);
  scene = new Transform();
  const definitions = [
    [strokeVertexShader(lineCurveVertexShader, 'curve', 3), lineFragmentShader, gl.TRIANGLE_STRIP,
      strokeAttributes(new Float32Array([0, 1, 0, 0, 1, 1]), 1, 3)],
    [pointVertexShader, pointFragmentShader, gl.POINTS, { gridPoint: { size: 2, data: new Float32Array([0, 0]) } }],
    [strokeVertexShader(borderVertexShader, 'position', 2), lineFragmentShader, gl.TRIANGLE_STRIP,
      strokeAttributes(new Float32Array([0, 0, 1, 0, 1, 1, 0, 1]), 2, 2)],
  ];
  for (const [vertex, fragment, mode, attributes] of definitions) {
    const program = new Program(gl, {
      vertex, fragment, uniforms, transparent: true,
      cullFace: null, depthTest: false, depthWrite: false,
    });
    programs.push(program);
    const geometry = new Geometry(gl, attributes);
    const mesh = new Mesh(gl, { geometry, program, mode, frustumCulled: false });
    mesh.setParent(scene);
    meshes.push(mesh);
  }
  ready = true;
  resize();
  window.addEventListener('resize', scheduleResize);
  document.addEventListener('visibilitychange', syncPlayback);
  window.addEventListener('pagehide', dispose, { once: true });
  startTimer = window.setTimeout(() => {
    startDelayFinished = true;
    syncPlayback();
  }, INITIAL_ANIMATION_DELAY_MS);
} catch (failure) {
  error = String(failure);
  console.error('Web home background:', failure);
}
