/**
 * 极简 Markdown → HTML，只覆盖模型实际会吐的那几种符号（标题 / 有序无序列表 / 粗体 / 行内代码 / 分隔线）。
 *
 * 为什么不引 marked：四十行够用，少一个依赖，也省得给 DOMPurify 再引一个。
 * 不做原样输出的原因：先整体转义再替换标记，正文里即使出现 <script> 也只会被当字面量显示。
 */

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }

function esc(s) {
  return s.replace(/[&<>"']/g, c => ESC[c])
}

/** 行内标记。入参必须是已经转义过的文本，否则会把标签一起替换坏 */
function inline(s) {
  return s
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
    // 单个 * 当斜体只在两侧不是字母数字时收，免得 0.5*10^3 这种表达式被吃掉
    .replace(/(^|[\s（(])\*([^*\n]+)\*(?=[\s）)，。、,.!?！？]|$)/g, '$1<em>$2</em>')
}

/**
 * @param {string} raw 模型返回的原文
 * @returns {string} 可直接放进 v-html 的 HTML
 */
export function renderMarkdown(raw) {
  if (!raw) return ''
  const lines = String(raw).replace(/\r\n?/g, '\n').split('\n')
  const out = []
  let list = null // { tag: 'ul' | 'ol', items: [] }

  const flush = () => {
    if (!list) return
    out.push(`<${list.tag}>${list.items.map(t => `<li>${inline(esc(t))}</li>`).join('')}</${list.tag}>`)
    list = null
  }

  for (const rawLine of lines) {
    const line = rawLine.replace(/\s+$/, '')
    if (!line.trim()) {
      flush()
      continue
    }

    // --- / *** / ___ 分隔线：面板里没位置，直接丢
    if (/^\s*([-*_])\1{2,}\s*$/.test(line)) {
      flush()
      continue
    }

    // # 标题：整体下沉两级，# → h3，免得一个 h1 撑满整个气泡
    let m = line.match(/^\s{0,3}(#{1,6})\s+(.*)$/)
    if (m) {
      flush()
      const lv = Math.min(m[1].length + 2, 6)
      out.push(`<h${lv}>${inline(esc(m[2]))}</h${lv}>`)
      continue
    }

    // - * + 无序列表
    m = line.match(/^\s*[-*+]\s+(.*)$/)
    if (m) {
      if (!list || list.tag !== 'ul') {
        flush()
        list = { tag: 'ul', items: [] }
      }
      list.items.push(m[1])
      continue
    }

    // 1. / 1) 有序列表
    m = line.match(/^\s*\d+[.)]\s+(.*)$/)
    if (m) {
      if (!list || list.tag !== 'ol') {
        flush()
        list = { tag: 'ol', items: [] }
      }
      list.items.push(m[1])
      continue
    }

    // > 引用：模型偶尔用来强调，降级成普通段落就够
    m = line.match(/^\s*>\s?(.*)$/)
    if (m) {
      flush()
      out.push(`<p>${inline(esc(m[1]))}</p>`)
      continue
    }

    flush()
    out.push(`<p>${inline(esc(line))}</p>`)
  }

  flush()
  return out.join('')
}
