export function strokeAttributes(segments, pathCount, components) {
  const segmentsPerPath = segments.length / (2 * pathCount * components);
  const pointsPerPath = segmentsPerPath + 1;
  const vertexCount = pointsPerPath * 2 * pathCount + (pathCount - 1) * 2;
  const points = new Float32Array(vertexCount * components);
  const previous = new Float32Array(points.length);
  const next = new Float32Array(points.length);
  const sides = new Float32Array(vertexCount);
  let vertex = 0;

  function sampleOffset(pathOffset, pointIndex) {
    return pathOffset + Math.min(pointIndex * 2, segmentsPerPath * 2 - 1) * components;
  }

  function append(pathOffset, pointIndex, side) {
    const point = sampleOffset(pathOffset, pointIndex);
    const before = sampleOffset(pathOffset, Math.max(0, pointIndex - 1));
    const after = sampleOffset(pathOffset, Math.min(segmentsPerPath, pointIndex + 1));
    const offset = vertex * components;
    for (let component = 0; component < components; component++) {
      points[offset + component] = segments[point + component];
      previous[offset + component] = segments[before + component];
      next[offset + component] = segments[after + component];
    }
    sides[vertex++] = side;
  }

  for (let pathIndex = 0; pathIndex < pathCount; pathIndex++) {
    const pathOffset = pathIndex * segmentsPerPath * 2 * components;
    if (pathIndex > 0) append(pathOffset, 0, -1);
    for (let pointIndex = 0; pointIndex < pointsPerPath; pointIndex++) {
      append(pathOffset, pointIndex, -1);
      append(pathOffset, pointIndex, 1);
    }
    if (pathIndex < pathCount - 1) append(pathOffset, segmentsPerPath, 1);
  }

  return {
    strokePoint: { size: components, data: points },
    strokeBefore: { size: components, data: previous },
    strokeAfter: { size: components, data: next },
    strokeSide: { size: 1, data: sides },
  };
}

export function strokeVertexShader(source, attribute, components) {
  const pointType = `vec${components}`;
  const input = `attribute ${pointType} ${attribute};`;
  const entry = 'void main() {';
  const output = 'gl_Position = vec4(clipPosition, 0.0, 1.0);';
  if (![input, entry, output].every(part => source.includes(part))) {
    throw new Error(`Unexpected Web background vertex shader: ${attribute}`);
  }
  const sampleShader = source.replace(input, '')
    .replace(entry, `vec2 resolveStrokePoint(${pointType} ${attribute}) {`)
    .replace(output, 'return clipPosition;');

  return `${sampleShader}
attribute ${pointType} strokePoint;
attribute ${pointType} strokeBefore;
attribute ${pointType} strokeAfter;
attribute float strokeSide;
uniform float uStrokeHalfWidth;
uniform float uStrokeFeather;
varying float vStrokeOffset;
varying float vStrokeFeather;

void main() {
  vec2 pixelsPerClipUnit = uResolution * vec2(0.5, -0.5);
  vec2 center = resolveStrokePoint(strokePoint);
  vec2 incoming = (center - resolveStrokePoint(strokeBefore)) * pixelsPerClipUnit;
  vec2 outgoing = (resolveStrokePoint(strokeAfter) - center) * pixelsPerClipUnit;
  if (dot(incoming, incoming) < 0.0001) incoming = outgoing;
  if (dot(outgoing, outgoing) < 0.0001) outgoing = incoming;
  vec2 before = incoming / max(length(incoming), 0.0001);
  vec2 after = outgoing / max(length(outgoing), 0.0001);
  vec2 tangent = before + after;
  tangent = dot(tangent, tangent) < 0.0001 ? after : normalize(tangent);
  vec2 normal = vec2(-tangent.y, tangent.x);
  float miter = max(0.5, dot(normal, vec2(-after.y, after.x)));
  vStrokeFeather = uStrokeFeather * max(1.0, abs(normal.x) + abs(normal.y));
  vStrokeOffset = strokeSide * (uStrokeHalfWidth + vStrokeFeather);
  vec2 offset = normal * vStrokeOffset / (miter * pixelsPerClipUnit);
  gl_Position = vec4(center + offset, 0.0, 1.0);
}
`;
}
