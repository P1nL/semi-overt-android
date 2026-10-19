// Native port of SO Solid Card Bend reference shaders, 2026-10-17.

precision highp float;
uniform sampler2D tCard;
uniform vec3 cameraPosition;
uniform float uMaterial;
uniform float uCoverBlur;
uniform vec3 uBackColor;
varying vec2 vUv;
varying vec3 vNormal;
varying vec3 vWorld;
vec3 cardColor(vec2 uv) {
  if (uv.y <= 0.5001 || uCoverBlur < 0.15) return texture2D(tCard, uv).rgb;
  vec3 sum = vec3(0.0);
  float total = 0.0;
  vec2 stepUV = vec2(uCoverBlur / 372.0, uCoverBlur / 352.0) * 0.6;
  for (int x=-2; x<=2; x++) {
    for (int y=-2; y<=2; y++) {
      float w = exp(-0.5 * float(x*x + y*y));
      vec2 sampleUV = clamp(uv + vec2(float(x),float(y))*stepUV,vec2(0.001,0.501),vec2(0.999));
      sum += texture2D(tCard,sampleUV).rgb*w;
      total += w;
    }
  }
  return sum/total;
}
void main() {
  vec3 base = uMaterial < 0.5 ? cardColor(vUv) : (uMaterial < 1.5 ? uBackColor : vec3(0.96,0.985,0.93));
  vec3 n = normalize(vNormal);
  vec3 light = normalize(vec3(-0.25,0.32,1.30));
  vec3 view = normalize(cameraPosition-vWorld);
  float diffuse = max(dot(n,light),0.0);
  float rim = pow(1.0-max(dot(n,view),0.0),3.0);
  float highlight = pow(max(dot(n,normalize(light+view)),0.0),24.0)*0.035;
  float lighting = uMaterial > 1.5 ? 0.76 + 0.32*diffuse : 0.64 + 0.43*diffuse;
  vec3 linear = pow(base,vec3(2.2))*lighting + vec3(highlight + rim*0.008);
  gl_FragColor=vec4(pow(max(linear,vec3(0.0)),vec3(1.0/2.2)),1.0);
}
