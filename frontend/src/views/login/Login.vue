<template>
  <div class="login-page">
    <div class="login-grid">
      <!-- 左：品牌 -->
      <section class="brand-side">
        <div class="brand-head">
          <div class="mark">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#F5B942" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M13 2L3 14h9l-1 8 10-12h-9l1-8z" />
            </svg>
          </div>
          <span class="mark-text">POWERGRID</span>
        </div>

        <div class="brand-main">
          <h1>电力管理系统</h1>
          <p class="lede">
            从供电区域、变电站、设备台账，到电表计量、电费结算与告警处置，
            一套系统管理到底。
          </p>

          <ul class="features">
            <li>
              <span class="f-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6" />
                </svg>
              </span>
              <div class="f-text">
                <strong>设备与电表台账</strong>
                <span>供电区域 · 变电站 · 设备 · 智能电表，逐级管理</span>
              </div>
            </li>
            <li>
              <span class="f-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="3" y="4" width="18" height="16" rx="2" />
                  <path d="M3 10h18M8 15h4" />
                </svg>
              </span>
              <div class="f-text">
                <strong>电费结算与在线缴费</strong>
                <span>按阶梯电价自动出账，客户可在线缴纳与查询</span>
              </div>
            </li>
            <li>
              <span class="f-icon">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 8a6 6 0 10-12 0c0 7-3 8-3 8h18s-3-1-3-8M10.3 21a2 2 0 003.4 0" />
                </svg>
              </span>
              <div class="f-text">
                <strong>告警处置与检修跟踪</strong>
                <span>异常实时上报，检修工单闭环管理</span>
              </div>
            </li>
          </ul>
        </div>

        <div class="brand-foot">
          <span class="dot"></span>
          <span>Power Management System</span>
          <span class="ver">v1.0</span>
        </div>
      </section>

      <!-- 右：表单 -->
      <section class="form-side">
        <div class="form-inner">
          <header class="form-head">
            <h2>欢迎登录</h2>
            <p>请输入账号密码进入系统</p>
          </header>

          <el-form ref="formRef" :model="form" :rules="rules" class="login-form" @keyup.enter="handleLogin">
            <label class="field-label">用户名</label>
            <el-form-item prop="username">
              <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" size="large" />
            </el-form-item>

            <label class="field-label">密码</label>
            <el-form-item prop="password">
              <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password :prefix-icon="Lock" size="large" />
            </el-form-item>

            <el-form-item class="submit-item">
              <el-button type="primary" @click="handleLogin" :loading="loading" class="login-btn" size="large">
                {{ loading ? '验证中…' : '进入系统' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-note">© 2026 Power Management System</div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: 'admin',
  password: 'admin123'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => {})
  if (!valid) return
  loading.value = true
  try {
    await userStore.login(form)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  background: #08080A;
  display: flex;
  align-items: stretch;
  position: relative;
  overflow: hidden;
}

/* 顶部金色氛围光 */
.login-page::before {
  content: '';
  position: absolute;
  top: -280px;
  left: -160px;
  width: 760px;
  height: 560px;
  background: radial-gradient(closest-side, rgba(245, 185, 66, 0.09), transparent 70%);
  pointer-events: none;
}

/* 右下淡网格纹理 */
.login-page::after {
  content: '';
  position: absolute;
  right: 0;
  bottom: 0;
  width: 46%;
  height: 60%;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.025) 1px, transparent 1px);
  background-size: 44px 44px;
  -webkit-mask-image: radial-gradient(closest-side at 100% 100%, #000, transparent);
  mask-image: radial-gradient(closest-side at 100% 100%, #000, transparent);
  pointer-events: none;
}

.login-grid {
  display: grid;
  grid-template-columns: 1fr 460px;
  width: 100%;
  max-width: 1240px;
  margin: 0 auto;
  padding: 48px 40px;
  gap: 48px;
  align-items: center;
  position: relative;
  z-index: 1;
}

/* ---------- 品牌侧 ---------- */
.brand-side {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 480px;
  padding: 32px 0;
}

.brand-head {
  display: flex;
  align-items: center;
  gap: 9px;
}

.mark {
  width: 28px;
  height: 28px;
  border-radius: 5px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(245, 185, 66, 0.1);
  box-shadow: 0 0 0 1px rgba(245, 185, 66, 0.26);
}

.mark-text {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: var(--text-primary);
}

.brand-main h1 {
  font-size: 42px;
  line-height: 1.2;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin-bottom: 18px;
}

.lede {
  font-size: 13.5px;
  line-height: 1.9;
  color: var(--text-muted);
  max-width: 430px;
  margin-bottom: 38px;
}

/* 三行业务能力：图标 + 主标题 + 说明 */
.features {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 22px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  padding-top: 28px;
  max-width: 430px;
}

.features li {
  display: flex;
  align-items: flex-start;
  gap: 13px;
}

.f-icon {
  flex: none;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--gold);
  background: rgba(245, 185, 66, 0.07);
  box-shadow: 0 0 0 1px rgba(245, 185, 66, 0.16);
  margin-top: 1px;
}

.f-text strong {
  display: block;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 4px;
}

.f-text span {
  font-size: 12px;
  line-height: 1.6;
  color: var(--text-faint);
}

.brand-foot {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 11px;
  color: var(--text-faint);
  font-family: var(--font-mono);
  letter-spacing: 0.03em;
}

.brand-foot .dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--gold);
  opacity: 0.7;
}

.brand-foot .ver { opacity: 0.5; }

/* ---------- 表单侧 ---------- */
.form-side {
  background: var(--bg-card);
  border-radius: 10px;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.06), 0 40px 80px -40px rgba(0, 0, 0, 0.9);
  padding: 40px 36px 28px;
}

.form-inner { width: 100%; }

.form-head { margin-bottom: 28px; }

.form-head h2 {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.01em;
  margin-bottom: 7px;
}

.form-head p {
  font-size: 12.5px;
  color: var(--text-faint);
}

.field-label {
  display: block;
  font-size: 11.5px;
  color: var(--text-muted);
  margin-bottom: 7px;
  letter-spacing: 0.02em;
}

.login-form :deep(.el-form-item) { margin-bottom: 18px; }

.login-form :deep(.el-input__wrapper) {
  padding: 1px 12px !important;
  height: 42px !important;
  border-radius: 5px !important;
  background: #0C0C0E !important;
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.07) inset !important;
}

.login-form :deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.14) inset !important;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px rgba(245, 185, 66, 0.5) inset !important;
  background: #0E0E10 !important;
}

.login-form :deep(.el-input__inner) {
  height: 40px !important;
  font-size: 13px !important;
  color: var(--text-primary) !important;
}

.login-form :deep(.el-input__prefix-inner .el-icon) {
  font-size: 14px;
  color: var(--text-faint) !important;
}

.submit-item { margin-top: 26px !important; margin-bottom: 0 !important; }

.login-btn {
  width: 100%;
  height: 42px;
  font-size: 13.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  border-radius: 5px !important;
  background: var(--gold) !important;
  border: none !important;
  color: #14100A !important;
}

.login-btn:hover {
  background: var(--gold-bright) !important;
  box-shadow: 0 0 24px -6px rgba(245, 185, 66, 0.4) !important;
}

.form-note {
  margin-top: 24px;
  padding-top: 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.05);
  font-size: 11px;
  color: var(--text-faint);
  text-align: center;
  letter-spacing: 0.03em;
}

@media (max-width: 1000px) {
  .login-grid { grid-template-columns: 1fr; max-width: 420px; padding: 32px 20px; gap: 24px; }
  .brand-side { min-height: auto; padding: 8px 0; }
  .brand-main h1 { font-size: 28px; }
  .lede, .features { display: none; }
}
</style>
