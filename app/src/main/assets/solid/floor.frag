// Native port of SO Solid Card Bend reference shaders, 2026-10-17.

precision highp float;
uniform sampler2D tShadow;
uniform vec3 uPageColor;
varying vec4 vShadow;
varying vec3 vWorld;
float unpackRGBA(vec4 v) {return dot(v,vec4(1.0,1.0/255.0,1.0/65025.0,1.0/16581375.0));}
void main() {
  vec3 s=vShadow.xyz/vShadow.w*0.5+0.5;
  float shade=0.0;
  if(s.x>0.0 && s.x<1.0 && s.y>0.0 && s.y<1.0 && s.z<1.0) {
    for(int x=-2;x<=2;x++) {
      for(int y=-2;y<=2;y++) {
        float z=unpackRGBA(texture2D(tShadow,s.xy+vec2(float(x),float(y))*2.5/1024.0));
        shade+=step(z+0.0018,s.z);
      }
    }
    shade/=25.0;
  }
  float warmth=clamp((vWorld.y+500.0)/1500.0,0.0,1.0);
  vec3 base=uPageColor;
  gl_FragColor=vec4(base*(1.0-shade*0.095),1.0);
}
