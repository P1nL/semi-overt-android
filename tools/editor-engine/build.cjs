const fs = require('node:fs');
const path = require('node:path');
const webRoot = process.env.SEMI_WEB_ROOT || 'D:/works/semi-overt';
const modules = path.join(webRoot, 'node_modules');
const esbuild = require(path.join(modules, 'esbuild'));
const output = path.resolve(__dirname, '../../app/src/main/assets/editor/vendor');

async function build() {
  const result = await esbuild.build({
    entryPoints: [path.join(__dirname, 'editor-engine.js')],
    outfile: path.join(output, 'editor-engine.bundle.js'),
    bundle: true,
    minify: true,
    format: 'iife',
    globalName: 'SemiEditor',
    target: ['chrome110', 'safari16.4'],
    nodePaths: [modules],
    alias: {
      '@semi/editor-markdown': path.join(webRoot, 'src/features/article-editor/model/editor-markdown.ts'),
      '@semi/editor-image': path.join(webRoot, 'src/features/article-editor/model/editor-image.ts'),
      '@': path.join(webRoot, 'src'),
    },
    legalComments: 'eof',
    metafile: true,
  });
  const licenses = new Map();
  for (const input of Object.keys(result.metafile.inputs)) {
    let directory = path.dirname(path.resolve(input));
    while (directory.includes('node_modules')) {
      const manifest = path.join(directory, 'package.json');
      if (fs.existsSync(manifest)) {
        const info = JSON.parse(fs.readFileSync(manifest, 'utf8'));
        const license = ['LICENSE', 'LICENSE.md', 'LICENSE.txt', 'license', 'license.md']
          .map((file) => path.join(directory, file)).find((file) => fs.existsSync(file));
        if (license) licenses.set(info.name, `${info.name}\n\n${fs.readFileSync(license, 'utf8')}`);
        break;
      }
      directory = path.dirname(directory);
    }
  }
  fs.writeFileSync(path.join(output, 'editor-dependencies.LICENSE'),
    [...licenses.values()].join('\n\n----------------------------------------\n\n'), 'utf8');
  console.log('Editor bundle built using the web editor dependencies and Markdown serializer.');
}
build().catch((error) => { console.error(error); process.exitCode = 1; });
