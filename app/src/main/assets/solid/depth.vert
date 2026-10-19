// Native port of SO Solid Card Bend reference shaders, 2026-10-17.

precision highp float;
attribute vec3 position;
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;

uniform float uScroll;
uniform float uRadius;
uniform float uThickness;
uniform float uReturnLine;
uniform float uLayer;

vec3 loopPoint(float localY) {
  float straight = uReturnLine - 162.0;
  float arc = 3.141592653589793 * uRadius;
  float period = 2.0 * (straight + arc);
  float distance = mod(uScroll - 24.0 - localY, period);
  if (distance < arc) {
    float angle = distance / uRadius;
    return vec3(162.0 - uRadius*sin(angle), -uRadius*(1.0-cos(angle)), angle);
  }
  if (distance < arc + straight) {
    return vec3(162.0 + distance-arc, -2.0*uRadius, 3.141592653589793);
  }
  if (distance < 2.0*arc + straight) {
    float turn = (distance-arc-straight) / uRadius;
    return vec3(uReturnLine + uRadius*sin(turn), -uRadius*(1.0+cos(turn)), 3.141592653589793+turn);
  }
  return vec3(uReturnLine - (distance-2.0*arc-straight), 0.0, 6.283185307179586);
}

vec3 bendPosition(vec3 p) {
  vec3 route=loopPoint(p.y);
  float angle=route.z;
  vec3 surfaceNormal = vec3(0.0, sin(angle), cos(angle));
  return vec3(p.x, -route.x, route.y) + surfaceNormal * (p.z * uThickness + uLayer);
}
vec3 bendNormal(vec3 n, float angle) {
  return normalize(vec3(n.x, n.y*cos(angle) + n.z*sin(angle), -n.y*sin(angle) + n.z*cos(angle)));
}
void main() {
  gl_Position = projectionMatrix*modelViewMatrix*vec4(bendPosition(position),1.0);
}
