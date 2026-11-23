const fs = require('node:fs');
const path = require('node:path');

const webRoot = process.env.SEMI_WEB_ROOT || path.resolve(__dirname, '../../../semi-overt');
const modules = path.join(webRoot, 'node_modules');
const esbuild = require(path.join(modules, 'esbuild'));
const source = fs.readFileSync(path.join(webRoot, 'src/widgets/app-background/AppBackground.vue'), 'utf8');
const output = path.resolve(__dirname, '../../app/src/main/assets/web-background');
const constants = [
  'INITIAL_ANIMATION_DELAY_MS',
  'LINE_GAP_PX', 'POINT_GAP_PX', 'CURVE_SUBDIVISIONS',
];
const shaders = [
  'pointerWarpShader', 'lineCurveVertexShader', 'gridPointVertexShader',
  'borderVertexShader',
];
const functions = ['createLineCurveData', 'createPointData'];

function extract(pattern, name) {
  const match = source.match(pattern);
  if (!match) throw new Error(`Web background declaration not found: ${name}`);
  return match[0];
}

const declarations = [
  ...constants.map(name => extract(new RegExp(`^const ${name} = [^\\r\\n]+$`, 'm'), name)),
  ...shaders.map(name => extract(new RegExp('^const ' + name + ' = `[\\s\\S]*?^`;?\\r?$', 'm'), name)),
  ...functions.map(name => extract(new RegExp(`^function ${name}\\([\\s\\S]*?^\\}`, 'm'), name)),
].join('\n\n');

async function build() {
  fs.mkdirSync(output, { recursive: true });
  await esbuild.build({
    entryPoints: [path.join(__dirname, 'runtime.js')],
    outfile: path.join(output, 'background.bundle.js'),
    bundle: true,
    minify: true,
    format: 'iife',
    target: ['chrome110'],
    nodePaths: [modules],
    legalComments: 'eof',
    plugins: [{
      name: 'semi-web-background-source',
      setup(build) {
        build.onResolve({ filter: /^@semi\/web-background-source$/ }, () => ({
          path: 'AppBackground.vue',
          namespace: 'semi-web-background',
        }));
        build.onLoad({ filter: /.*/, namespace: 'semi-web-background' }, () => ({
          contents: `${declarations}\nexport { ${[...constants, ...shaders, ...functions].join(', ')} };`,
          loader: 'ts',
        }));
      },
    }],
  });
  const oglReadme = fs.readFileSync(path.join(modules, 'ogl/README.md'), 'utf8');
  const oglLicense = oglReadme.match(/^## Unlicense\s+([\s\S]+)/m)?.[1];
  if (!oglLicense) throw new Error('OGL Unlicense notice not found.');
  fs.writeFileSync(path.join(output, 'ogl.LICENSE'), `OGL\n\n${oglLicense}`, 'utf8');
  console.log('Bundled the web AppBackground geometry, Android shaders, and OGL renderer.');
}

build().catch(error => { console.error(error); process.exitCode = 1; });
