import { Editor, Extension, Mark, mergeAttributes } from '@tiptap/core';
import Heading from '@tiptap/extension-heading';
import Highlight from '@tiptap/extension-highlight';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import Placeholder from '@tiptap/extension-placeholder';
import { Table } from '@tiptap/extension-table';
import { TableCell } from '@tiptap/extension-table-cell';
import { TableHeader } from '@tiptap/extension-table-header';
import { TableRow } from '@tiptap/extension-table-row';
import TextAlign from '@tiptap/extension-text-align';
import StarterKit from '@tiptap/starter-kit';
import { Plugin, PluginKey, Selection, TextSelection } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';
import { CellSelection, TableMap } from '@tiptap/pm/tables';
import {
  renderMarkdownToEditorHtml,
  serializeEditorToMarkdown,
} from '@semi/editor-markdown';
import { extractEditorImageWidthPercentFromElement } from '@semi/editor-image';

export { renderMarkdownToEditorHtml, serializeEditorToMarkdown };

const Underline = Mark.create({
  name: 'underline',
  parseHTML: () => [
    { tag: 'u' },
    { style: 'text-decoration', getAttrs: (value) => String(value).includes('underline') ? {} : false },
  ],
  renderHTML: ({ HTMLAttributes }) => ['u', mergeAttributes(HTMLAttributes), 0],
});

const EditorImage = Image.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      widthPercent: {
        default: null,
        parseHTML: extractEditorImageWidthPercentFromElement,
        renderHTML: ({ widthPercent }) => widthPercent == null ? {} : {
          'data-width': widthPercent,
          style: `width: ${widthPercent}%; height: auto;`,
        },
      },
    };
  },
});

const linkDraftKey = new PluginKey('mobile-link-draft');
const LinkDraft = Extension.create({
  name: 'mobileLinkDraft',
  addProseMirrorPlugins() {
    return [new Plugin({
      key: linkDraftKey,
      state: {
        init: () => null,
        apply(tr, range) {
          const next = tr.getMeta(linkDraftKey);
          if (next !== undefined) return next;
          if (!range || !tr.docChanged) return range;
          return { from: tr.mapping.map(range.from, -1), to: tr.mapping.map(range.to, 1) };
        },
      },
      props: {
        decorations(state) {
          const range = linkDraftKey.getState(state);
          return range && range.to > range.from ? DecorationSet.create(state.doc, [
            Decoration.inline(range.from, range.to, { class: 'pending-link-text' }),
          ]) : DecorationSet.empty;
        },
      },
    })];
  },
});

export function setLinkDraft(editor, range) {
  editor.view.dispatch(editor.state.tr.setMeta(linkDraftKey, range));
}
export function getLinkDraft(editor) { return linkDraftKey.getState(editor.state); }

export function createEditor({ element, content, onUpdate, onSelectionUpdate, onTransaction }) {
  return new Editor({
    element,
    extensions: [
      StarterKit.configure({ heading: false, link: false, underline: false }),
      Heading.configure({ levels: [1, 2, 3, 4, 5] }),
      Link.extend({ inclusive: false }).configure({
        openOnClick: false,
        autolink: true,
        linkOnPaste: true,
        defaultProtocol: 'https',
        HTMLAttributes: { rel: 'noopener noreferrer nofollow', target: '_blank' },
      }),
      Highlight.configure({ multicolor: false }),
      Underline,
      LinkDraft,
      TextAlign.configure({
        types: ['heading', 'paragraph', 'listItem'],
        alignments: ['left', 'center', 'right'],
        defaultAlignment: 'left',
      }),
      EditorImage.configure({ inline: false, allowBase64: true }),
      Table.configure({ resizable: true, lastColumnResizable: true, cellMinWidth: 56 }),
      TableRow,
      TableHeader,
      TableCell,
      Placeholder.configure({ placeholder: '正文内容…' }),
    ],
    content: renderMarkdownToEditorHtml(content),
    parseOptions: { preserveWhitespace: true },
    editorProps: {
      attributes: {
        role: 'textbox',
        'aria-label': '正文',
        'aria-multiline': 'true',
        spellcheck: 'false',
      },
    },
    onUpdate,
    onSelectionUpdate,
    onTransaction,
  });
}

export function tableInfo(editor, at = null) {
  const { selection } = editor.state;
  const $pos = at != null ? editor.state.doc.resolve(at) :
    selection instanceof CellSelection ? selection.$anchorCell : selection.$from;
  let node, pos;
  for (let depth = $pos.depth; depth > 0; depth--) {
    if ($pos.node(depth).type.spec.tableRole === 'table') {
      node = $pos.node(depth); pos = $pos.before(depth); break;
    }
  }
  if (!node && selection.node?.type.spec.tableRole === 'table') {
    node = selection.node; pos = selection.from;
  }
  if (!node) return null;
  const map = TableMap.get(node), start = pos + 1;
  const dom = editor.view.nodeDOM(pos);
  const element = dom?.matches?.('table') ? dom : dom?.querySelector?.('table');
  let rect = { top: 0, left: 0, bottom: map.height, right: map.width };
  if (at == null && selection instanceof CellSelection) {
    rect = map.rectBetween(selection.$anchorCell.pos - start, selection.$headCell.pos - start);
  }
  return { node, pos, start, map, element, rect, rows: map.height, cols: map.width,
    header: node.firstChild.firstChild.type.spec.tableRole === 'header_cell',
    cellSelection: selection instanceof CellSelection };
}

export function cellPosition(editor, element) {
  const pos = editor.view.posAtDOM(element, 0), $pos = editor.state.doc.resolve(pos);
  for (let depth = $pos.depth; depth > 0; depth--) {
    if (['cell', 'header_cell'].includes($pos.node(depth).type.spec.tableRole)) return $pos.before(depth);
  }
  return null;
}

export function selectTableRange(editor, mode, anchor, head = anchor) {
  const info = tableInfo(editor, anchor);
  if (!info) return false;
  if (mode === 'all') {
    anchor = info.start + info.map.map[0];
    head = info.start + info.map.map.at(-1);
  }
  if (anchor == null || head == null) return false;
  const $anchor = editor.state.doc.resolve(anchor), $head = editor.state.doc.resolve(head);
  const selection = mode === 'row' ? CellSelection.rowSelection($anchor, $head) :
    mode === 'column' ? CellSelection.colSelection($anchor, $head) : new CellSelection($anchor, $head);
  editor.view.dispatch(editor.state.tr.setSelection(selection));
  return true;
}

// Setup resizes only the newly inserted regular grid; retained cells keep their text.
export function resizeNewTable(editor, rows, cols, header) {
  const info = tableInfo(editor);
  if (!info) return false;
  const { schema } = editor.state;
  const content = Array.from({ length: rows }, (_, row) => {
    const oldRow = row < info.rows ? info.node.child(row) : null;
    return schema.nodes.tableRow.create(null, Array.from({ length: cols }, (_, col) => {
      const old = oldRow && col < oldRow.childCount ? oldRow.child(col) : null;
      const type = row === 0 && header ? schema.nodes.tableHeader : schema.nodes.tableCell;
      return type.create(null, old?.content || schema.nodes.paragraph.create());
    }));
  });
  const table = schema.nodes.table.create(info.node.attrs, content);
  const tr = editor.state.tr.replaceWith(info.pos, info.pos + info.node.nodeSize, table);
  tr.setSelection(TextSelection.near(tr.doc.resolve(info.pos + 1)));
  editor.view.dispatch(tr);
  return true;
}

const segmenter = new Intl.Segmenter('zh-CN', { granularity: 'grapheme' });

// Move through document positions, not UTF-16 units, so emoji stay intact.
export function moveCursor(editor, direction, extend = false) {
  const { state, view } = editor;
  const { doc, selection } = state;
  const horizontal = direction === 'left' || direction === 'right';
  const sign = direction === 'left' || direction === 'up' ? -1 : 1;
  let target = selection.head;
  if (horizontal && !extend && !selection.empty) {
    target = sign < 0 ? selection.from : selection.to;
  } else if (horizontal) {
    const position = doc.resolve(target);
    const offset = position.parentOffset;
    if (position.parent.isTextblock &&
        (sign > 0 ? offset < position.parent.content.size : offset > 0)) {
      const text = position.parent.textBetween(0, position.parent.content.size, '', '\ufffc');
      const boundaries = [...segmenter.segment(text)].map((item) => item.index);
      boundaries.push(text.length);
      const next = sign > 0 ? boundaries.find((index) => index > offset) :
        boundaries.filter((index) => index < offset).at(-1);
      target = position.start() + (next ?? offset);
    } else {
      const next = Selection.findFrom(
        doc.resolve(Math.max(0, Math.min(doc.content.size, target + sign))), sign, true,
      );
      if (!next) return false;
      target = next.head;
    }
  } else {
    const rect = view.coordsAtPos(target);
    const height = parseFloat(getComputedStyle(view.dom).lineHeight) || 30;
    const next = view.posAtCoords({
      left: rect.left,
      top: (rect.top + rect.bottom) / 2 + sign * height,
    });
    if (!next) return false;
    target = Selection.near(doc.resolve(next.pos), sign).head;
  }
  const anchor = extend ? selection.anchor : target;
  view.dispatch(state.tr.setSelection(TextSelection.create(doc, anchor, target)).scrollIntoView());
  return true;
}
