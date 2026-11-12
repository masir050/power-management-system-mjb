<template>
  <div class="rag">
    <!-- 面板 -->
    <transition name="rag-pop">
      <section v-if="open" class="rag-panel">
        <header class="rag-head">
          <span class="rag-mark">
            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="#F5B942" stroke-width="2.2"
                 stroke-linecap="round" stroke-linejoin="round">
              <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
            </svg>
          </span>
          <div class="rag-title">
            <div class="rag-name">电力规程助手</div>
            <div class="rag-sub">{{ subtitle }}</div>
          </div>
          <button class="rag-close" title="收起" @click="open = false">×</button>
        </header>

        <div class="rag-modes">
          <button :class="['rag-mode', { on: mode === 'ask' }]" @click="switchMode('ask')">规程问答</button>
          <!-- 告警研判是运维功能，用电用户不显示这个入口 -->
          <button v-if="viewerRole !== 'CUSTOMER'" :class="['rag-mode', { on: mode === 'diagnose' }]"
                  @click="switchMode('diagnose')">告警研判</button>
        </div>

        <div class="rag-roles">
          <span class="rag-roles-t">提问身份</span>
          <span class="rag-role on">{{ roleLabel }}</span>
          <span class="rag-roles-note">跟随登录账号，不可切换</span>
        </div>

        <div ref="scroller" class="rag-body">
          <div v-for="(m, i) in messages" :key="i" :class="['rag-msg', m.role]">
            <span v-if="m.role === 'bot'" class="rag-avatar">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
                   stroke-linecap="round" stroke-linejoin="round">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
              </svg>
            </span>
            <div class="rag-bubble">
              <!-- 模型的回答是 Markdown，渲染成真列表/加粗再显示；用户输入按纯文本走，不给注入机会 -->
              <div v-if="m.role === 'bot'" class="rag-text rag-rich" v-html="renderMarkdown(m.text)"></div>
              <div v-else class="rag-text">{{ m.text }}</div>

              <div v-if="m.sources && m.sources.length" class="rag-sources">
                <span v-for="(s, j) in m.sources" :key="j" class="rag-src">{{ j + 1 }}·{{ s }}</span>
              </div>

              <div v-if="m.steps" class="rag-steps">
                <button class="rag-steps-head" @click="m.showTrace = !m.showTrace">
                  工具调用 {{ m.steps }} 步 · {{ m.showTrace ? '收起' : '展开 trace' }}
                </button>
                <ol v-if="m.showTrace" class="rag-trace">
                  <li v-for="t in m.trace" :key="t.no">
                    <code>{{ t.tool }}</code><span class="rag-args">{{ t.args }}</span>
                  </li>
                </ol>
              </div>
            </div>
          </div>

          <div v-if="showPresets && !loading" class="rag-quick">
            <span class="rag-quick-t">不知道从哪问起？</span>
            <button v-for="p in presets" :key="p" class="rag-chip" @click="send(p)">{{ p }}</button>
          </div>

          <div v-if="loading" class="rag-msg bot">
            <span class="rag-avatar">
              <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"
                   stroke-linecap="round" stroke-linejoin="round">
                <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
              </svg>
            </span>
            <div class="rag-bubble rag-loading">
              <span class="rag-blink"></span>
              {{ mode === 'ask' ? '翻规程中…' : '查台账、看告警、顺供电路径（10 秒左右）…' }}
            </div>
          </div>

          <div v-if="error" class="rag-err">{{ error }}</div>
        </div>

        <footer class="rag-foot">
          <input
            v-model="text"
            class="rag-input"
            :placeholder="inputHint"
            :disabled="loading"
            @keyup.enter="send()"
          />
          <button class="rag-send" :disabled="loading || !text.trim()" @click="send()">发送</button>
        </footer>
      </section>
    </transition>

    <!-- 右下角角标 -->
    <button class="rag-fab" :class="{ on: open }" title="电力规程问答" @click="open = !open">
      <svg v-if="!open" width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9.6 9.6 0 0 1-3.8-.8L3 21l1.9-4.4A8.4 8.4 0 0 1 12 3a8.4 8.4 0 0 1 9 8.5z" />
      </svg>
      <svg v-else width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor"
           stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round">
        <path d="M6 6l12 12M18 6L6 18" />
      </svg>
    </button>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { askRag, diagnoseAlarm, ragStats } from '../api/rag'
import { renderMarkdown } from '../utils/markdown'
import { useUserStore } from '../stores/user'

const open = ref(false)
const mode = ref('ask')
const text = ref('')
const loading = ref(false)
const error = ref('')
const messages = ref([])
const showPresets = ref(false)
const scroller = ref(null)
const meta = ref(null)   // { docs, blocks }，取自 /stats，失败则用兜底文案

// 提问身份：只跟随登录角色，面板里不提供切换（谁登录就是谁的口吻）
const ROLES = [
  { id: 'OPERATOR', label: '运维人员' },
  { id: 'ADMIN', label: '管理员' },
  { id: 'CUSTOMER', label: '用电用户' }
]
const userStore = useUserStore()
const viewerRole = computed(() =>
  ROLES.some(r => r.id === userStore.role) ? userStore.role : 'OPERATOR'
)
const roleLabel = computed(() => ROLES.find(r => r.id === viewerRole.value)?.label || '运维人员')

const subtitle = computed(() =>
  meta.value?.blocks
    ? `${meta.value.docs} 份规程 · ${meta.value.blocks} 个知识块`
    : '规程问答 · 向量 + BM25 双路召回'
)

const inputHint = computed(() =>
  mode.value === 'diagnose'
    ? '粘贴一条告警文本，回车研判'
    : viewerRole.value === 'CUSTOMER'
      ? '电费、停电、报修都能问，回车发送'
      : '问一条规程问题，回车发送'
)

const PRESETS = {
  OPERATOR: [
    '油温到多少度算危急缺陷',
    '断路器多久小修一次',
    '96℃ 且冷却器全停 25 分钟怎么办'
  ],
  ADMIN: [
    '主变过温告警的处置流程和时限',
    '故障抢修各环节谁负责',
    '检修计划的审批环节有哪些'
  ],
  CUSTOMER: [
    '电费是怎么算出来的',
    '家里停电了怎么报修',
    '总开关老跳闸怎么办'
  ],
  diagnose: [
    '圣火令主变压器顶层油温 92 度，超过告警值，如何处理',
    '天鹰刀断路器 SF6 压力低告警，气压降至 0.35MPa'
  ]
}
const presets = computed(() =>
  mode.value === 'diagnose' ? PRESETS.diagnose : PRESETS[viewerRole.value]
)

/** 开场白按身份分三套：交代"我是谁、按谁的口吻答、边界在哪" */
function greeting() {
  const n = meta.value?.docs
  if (mode.value === 'diagnose') {
    return [
      '贴一条告警给我就行，原文最好——设备名、告警类型、数值，有什么贴什么。',
      '我会去查设备台账和最近的告警记录，再顺着供电关系看会影响哪些地方，最后给你处置顺序。'
    ]
  }
  switch (viewerRole.value) {
    case 'CUSTOMER':
      return [
        '您好！用电上的事都能问我：电费怎么算、停电了怎么办、家里跳闸怎么查，我先说结论，再告诉您具体怎么办。',
        '我只能答这些资料里写到的内容，答不了的我直说，告诉您去哪个渠道办。'
      ]
    case 'ADMIN':
      return [
        n
          ? `您好。这 ${n} 份资料我都过了一遍，规程和业务口径都在里面。`
          : '您好。规程和业务口径的资料我都过了一遍。',
        '您问处置流程、责任分工、时限要求这类问题，我按流程顺序答；规程没覆盖到的我会直说，再指个方向。'
      ]
    default:
      return [
        n
          ? `你好，值班辛苦了。这 ${n} 份规程我都翻过了——变压器、断路器、母线、二次回路，还有检修验收和告警处置。想问什么直接说，不用咬文嚼字。`
          : '你好，值班辛苦了。变压器、断路器、母线、二次回路，还有检修验收和告警处置这些规程，我都翻过了。想问什么直接说，不用咬文嚼字。',
        '我只按规程答，不替你想当然。碰上规程没覆盖到的，我会直说，再告诉你该往哪类文件里找。'
      ]
  }
}

function greet() {
  messages.value = []
  error.value = ''
  greeting().forEach(t => messages.value.push({ role: 'bot', text: t }))
  showPresets.value = true
  toBottom()
}

async function loadStats() {
  try {
    const s = await ragStats()
    meta.value = { docs: (s.sources || []).length, blocks: s.blocks || 0 }
  } catch {
    /* 服务没起就算了，副标题退回兜底文案 */
  }
}

watch(open, v => {
  if (!v) return
  if (!meta.value) loadStats()
  if (!messages.value.length) greet()
})

function switchMode(m) {
  if (mode.value === m) return
  mode.value = m
  greet()
}

/** 登录角色变了（重新登录/换号）＝换一套人设，开新会话最干净 */
watch(viewerRole, id => {
  if (id === 'CUSTOMER' && mode.value === 'diagnose') {
    mode.value = 'ask'
  }
  greet()
})

function toBottom() {
  nextTick(() => {
    if (scroller.value) scroller.value.scrollTop = scroller.value.scrollHeight
  })
}

function push(msg) {
  messages.value.push(msg)
  toBottom()
}

async function send(preset) {
  const q = (preset || text.value).trim()
  if (!q || loading.value) return

  text.value = ''
  error.value = ''
  showPresets.value = false
  push({ role: 'user', text: q })
  loading.value = true
  toBottom()

  try {
    if (mode.value === 'ask') {
      const data = await askRag(q, viewerRole.value)
      push({ role: 'bot', text: data.answer || '（没有返回内容）', sources: data.sources || [] })
    } else {
      const data = await diagnoseAlarm(q)
      push({
        role: 'bot',
        text: data.answer || '（没有返回内容）',
        steps: data.steps || 0,
        trace: data.trace || [],
        showTrace: false
      })
    }
  } catch (e) {
    const status = e.response?.status
    error.value = status === 401
      ? '登录已过期，请重新登录后再用。'
      : status === 404 || status === 503 || !e.response
        ? '规程服务未启动（power-rag，端口 8088）。'
        : (e.response?.data?.message || e.message || '请求失败')
  } finally {
    loading.value = false
    showPresets.value = true
    toBottom()
  }
}
</script>

<style scoped>
.rag { position: relative; }

/* ---------- 角标 ---------- */
.rag-fab {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 900;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  border: 1px solid var(--gold-line);
  background: rgba(20, 20, 22, 0.92);
  color: var(--gold);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 6px 22px rgba(0, 0, 0, 0.55), 0 0 0 6px rgba(245, 185, 66, 0.05);
  transition: transform 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}
.rag-fab:hover {
  transform: translateY(-2px);
  background: rgba(245, 185, 66, 0.12);
  box-shadow: 0 10px 26px rgba(0, 0, 0, 0.6), 0 0 0 6px rgba(245, 185, 66, 0.08);
}
.rag-fab.on { background: rgba(245, 185, 66, 0.14); }

/* 呼吸光点：提醒"这里有个能问的东西"，又不抢页面注意力 */
.rag-fab::after {
  content: '';
  position: absolute;
  top: 5px;
  right: 5px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--gold);
  box-shadow: 0 0 0 3px rgba(245, 185, 66, 0.12);
  animation: rag-pulse 2.4s ease-in-out infinite;
}
.rag-fab.on::after { display: none; }
@keyframes rag-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}

/* ---------- 面板 ---------- */
.rag-panel {
  position: fixed;
  right: 22px;
  bottom: 80px;
  z-index: 901;
  width: 396px;
  max-width: calc(100vw - 32px);
  height: 540px;
  max-height: calc(100vh - 120px);
  display: flex;
  flex-direction: column;
  background: var(--bg-card);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 10px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.66);
  overflow: hidden;
}

.rag-head {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  background: var(--bg-raised);
  flex-shrink: 0;
}
.rag-mark {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--gold-tint);
  box-shadow: 0 0 0 1px var(--gold-line);
  flex-shrink: 0;
}
.rag-title { min-width: 0; }
.rag-name { font-size: 12.5px; color: var(--text-primary); font-weight: 600; letter-spacing: 0.02em; }
.rag-sub { font-size: 10.5px; color: var(--text-muted); margin-top: 2px; }
.rag-close {
  margin-left: auto;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--text-faint);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}
.rag-close:hover { background: rgba(255, 255, 255, 0.06); color: var(--text-body); }

.rag-modes {
  display: flex;
  gap: 6px;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  flex-shrink: 0;
}
.rag-mode {
  flex: 1;
  height: 26px;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, 0.07);
  background: transparent;
  color: var(--text-muted);
  font-size: 11.5px;
  cursor: pointer;
  transition: all 0.14s ease;
}
.rag-mode.on {
  color: var(--gold);
  background: var(--gold-tint);
  border-color: var(--gold-line);
}

/* 提问身份：只读展示，跟随登录角色 */
.rag-roles {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px 8px;
  flex-shrink: 0;
}
.rag-roles-t { font-size: 10.5px; color: var(--text-faint); margin-right: 2px; }
.rag-role {
  height: 22px;
  padding: 0 9px;
  border-radius: 999px;
  border: 1px solid var(--gold-line);
  background: var(--gold-tint);
  color: var(--gold);
  font-size: 11px;
  line-height: 22px;
}
.rag-roles-note { font-size: 10.5px; color: var(--text-faint); margin-left: auto; }

/* ---------- 消息区 ---------- */
.rag-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 0;
}
/* 常见问法：胶囊式，跟随在开场白后面，点一个就收起 */
.rag-quick {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  padding: 2px 2px 0 27px;
}
.rag-quick-t { width: 100%; font-size: 10.5px; color: var(--text-faint); margin-bottom: 1px; }
.rag-chip {
  padding: 5px 9px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: var(--bg-input);
  color: var(--text-muted);
  font-size: 11px;
  line-height: 1.3;
  cursor: pointer;
  transition: all 0.14s ease;
}
.rag-chip:hover {
  border-color: var(--gold-line);
  background: var(--gold-tint);
  color: var(--gold);
}

.rag-msg { display: flex; gap: 7px; align-items: flex-start; }
.rag-msg.user { justify-content: flex-end; }
.rag-avatar {
  width: 20px;
  height: 20px;
  margin-top: 3px;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--gold);
  background: var(--gold-tint);
  box-shadow: 0 0 0 1px var(--gold-line);
}
.rag-bubble {
  max-width: 84%;
  padding: 8px 10px;
  border-radius: 7px;
  font-size: 12px;
  line-height: 1.7;
}
.rag-msg.user .rag-bubble {
  background: var(--gold-tint);
  border: 1px solid var(--gold-line);
  color: var(--text-primary);
}
.rag-msg.bot .rag-bubble {
  background: var(--bg-elevated);
  border: 1px solid rgba(255, 255, 255, 0.06);
  color: var(--text-body);
}
.rag-text { white-space: pre-wrap; word-break: break-word; }

/* ---------- 回答正文（Markdown 渲染后） ---------- */
.rag-rich { white-space: normal; }
.rag-rich :deep(p) { margin: 0 0 6px; }
.rag-rich :deep(p:last-child) { margin-bottom: 0; }
.rag-rich :deep(h1),
.rag-rich :deep(h2),
.rag-rich :deep(h3) {
  margin: 1px 0 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-primary);
  padding-bottom: 4px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.07);
}
.rag-rich :deep(h4),
.rag-rich :deep(h5),
.rag-rich :deep(h6) {
  margin: 6px 0 3px;
  font-size: 11.8px;
  font-weight: 600;
  color: var(--gold-dim);
}
.rag-rich :deep(h1:first-child),
.rag-rich :deep(h2:first-child),
.rag-rich :deep(h3:first-child),
.rag-rich :deep(h4:first-child) { margin-top: 0; }
.rag-rich :deep(ul),
.rag-rich :deep(ol) { margin: 0 0 6px; padding-left: 17px; }
.rag-rich :deep(ul:last-child),
.rag-rich :deep(ol:last-child) { margin-bottom: 0; }
.rag-rich :deep(li) { margin: 2px 0; }
.rag-rich :deep(li::marker) { color: var(--gold-dim); }
.rag-rich :deep(ul) { list-style: none; padding-left: 13px; }
.rag-rich :deep(ul > li) { position: relative; }
.rag-rich :deep(ul > li)::before {
  content: '';
  position: absolute;
  left: -10px;
  top: 8px;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: var(--gold-dim);
}
.rag-rich :deep(strong) { color: var(--text-primary); font-weight: 600; }
.rag-rich :deep(em) { font-style: normal; color: var(--gold); }
.rag-rich :deep(code) {
  font-family: var(--font-mono);
  font-size: 11px;
  padding: 1px 4px;
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.06);
  color: var(--gold-dim);
}

.rag-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 8px;
  padding-top: 7px;
  border-top: 1px dashed rgba(255, 255, 255, 0.07);
}
.rag-src {
  font-size: 10px;
  padding: 2px 5px;
  border-radius: 3px;
  color: var(--gold);
  background: var(--gold-tint);
  box-shadow: 0 0 0 1px var(--gold-line);
}

.rag-steps { margin-top: 8px; }
.rag-steps-head {
  border: none;
  background: transparent;
  padding: 0;
  color: var(--text-muted);
  font-size: 10.5px;
  cursor: pointer;
  text-decoration: underline dotted;
}
.rag-steps-head:hover { color: var(--gold); }
.rag-trace {
  margin: 6px 0 0;
  padding-left: 16px;
  font-size: 10.5px;
  color: var(--text-muted);
  line-height: 1.9;
}
.rag-trace code {
  font-family: var(--font-mono);
  color: var(--gold-dim);
  margin-right: 5px;
}
.rag-args { color: var(--text-faint); }

.rag-loading { display: flex; align-items: center; gap: 6px; color: var(--text-muted); }
.rag-blink {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--gold);
  animation: rag-pulse 1s ease-in-out infinite;
}
.rag-err {
  font-size: 11.5px;
  color: var(--red);
  background: var(--red-tint);
  border: 1px solid var(--red-line);
  border-radius: 5px;
  padding: 7px 9px;
  line-height: 1.6;
}

/* ---------- 输入 ---------- */
.rag-foot {
  display: flex;
  gap: 7px;
  padding: 9px 10px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  background: var(--bg-raised);
  flex-shrink: 0;
}
.rag-input {
  flex: 1;
  height: 30px;
  padding: 0 9px;
  border-radius: 5px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: var(--bg-input);
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
  min-width: 0;
}
.rag-input:focus { border-color: var(--gold-line); }
.rag-input::placeholder { color: var(--text-faint); }
.rag-send {
  height: 30px;
  padding: 0 12px;
  border-radius: 5px;
  border: 1px solid var(--gold-line);
  background: var(--gold-tint);
  color: var(--gold);
  font-size: 11.5px;
  cursor: pointer;
  flex-shrink: 0;
}
.rag-send:disabled { opacity: 0.4; cursor: not-allowed; }

.rag-pop-enter-active, .rag-pop-leave-active { transition: opacity 0.16s ease, transform 0.16s ease; }
.rag-pop-enter-from, .rag-pop-leave-to { opacity: 0; transform: translateY(8px) scale(0.985); }

@media (max-width: 900px) {
  .rag-panel { right: 10px; bottom: 74px; height: calc(100vh - 140px); }
  .rag-fab { right: 12px; bottom: 14px; }
}
</style>
