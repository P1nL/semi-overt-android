// Native port of SO Solid Card Bend reference shaders, 2026-10-17.

precision highp float;
attribute vec3 position;
uniform mat4 modelMatrix;
uniform mat4 modelViewMatrix;
uniform mat4 projectionMatrix;
uniform mat4 shadowProjectionMatrix;
uniform mat4 shadowViewMatrix;
varying vec4 vShadow;
varying vec3 vWorld;
void main() {
  vec4 world=modelMatrix*vec4(position,1.0);
  vWorld=world.xyz;
  vShadow=shadowProjectionMatrix*shadowViewMatrix*world;
  gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);
}
