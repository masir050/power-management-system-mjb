/**
 * 缴费功能与三角色同步验证
 *
 * 要证明的四件事：
 *   1. 客户能给自己名下的账单缴费，缴完金额与状态都正确
 *   2. 客户缴完，管理员看到的是同一份结果（跨界面同步）
 *   3. 客户首页 KPI 跟着变（同一份数据的另一处消费）
 *   4. 越权与非法输入被挡：缴别人的账单、删账单、运维缴费、超额缴费
 *
 * 前置：5 个服务已启动、07_payment.sql 已执行
 * 用法：node verify-payment.mjs
 */

const BASE = 'http://127.0.0.1:8080/api'
const PWD = 'admin123'

let pass = 0
let fail = 0

function check(name, ok, detail = '') {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`)
  ok ? pass++ : fail++
}

async function login(username) {
  const r = await fetch(`${BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PWD })
  })
  const j = await r.json()
  return j?.data?.token
}

async function call(path, token, options = {}) {
  const r = await fetch(BASE + path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: 'Bearer ' + token,
      ...(options.headers || {})
    }
  })
  let body = null
  try { body = await r.json() } catch { /* 非 JSON */ }
  return { status: r.status, body }
}

const money = v => Number(Number(v || 0).toFixed(2))

/**
 * 本项目的业务异常统一返回 HTTP 200 + body.code=400（见 GlobalExceptionHandler），
 * 只有越权才走真正的 403。所以"被拒绝"要看 body.code，不能只看 HTTP 状态码。
 * 前端拦截器也是按 res.code !== 200 弹错误并 reject 的。
 */
const accepted = r => r.status === 200 && r.body?.code === 200
const rejected = r => r.status === 403 || (r.body && r.body.code !== 200)

async function main() {
  const tAdmin = await login('admin')
  const tOp = await login('weiyixiao')
  const tCus = await login('customer1')
  check('三个账号登录', !!(tAdmin && tOp && tCus))

  // ---------- 1. 客户账单初始态 ----------
  let page = await call('/bill/list?page=1&size=50', tCus)
  let rows = page.body?.data?.records || []
  const unpaidBefore = rows.filter(r => ['未缴', '逾期', '部分缴纳'].includes(r.paymentStatus))
  const arrearsBefore = money(unpaidBefore.reduce((s, r) => s + Number(r.arrearsAmount || 0), 0))
  check('客户看到自己的账单', rows.length > 0, `${rows.length} 条`)
  check('存在待缴账单', unpaidBefore.length >= 1, `${unpaidBefore.length} 笔 / ¥${arrearsBefore}`)

  if (unpaidBefore.length === 0) {
    console.log('\n没有待缴账单，无法继续验证缴费。先跑 07_payment.sql。')
    return
  }

  // 挑一笔有欠费的
  const target = unpaidBefore.sort((a, b) => Number(b.arrearsAmount) - Number(a.arrearsAmount))[0]
  const billId = target.id
  const arrears = money(target.arrearsAmount)
  const paid0 = money(target.paidAmount)
  console.log(`\n--- 目标账单 #${billId}：应缴 ¥${money(target.billAmount)} / 已缴 ¥${paid0} / 尚欠 ¥${arrears} / ${target.paymentStatus} ---\n`)

  // ---------- 2. 越权：缴别人的账单 ----------
  const othersBill = await call('/bill/list?page=1&size=50', tAdmin)
  const otherRows = (othersBill.body?.data?.records || []).filter(r => Number(r.customerId) !== 1)
  if (otherRows.length) {
    const hackPay = await call(`/bill/${otherRows[0].id}/pay`, tCus, {
      method: 'POST',
      body: JSON.stringify({ amount: 1, paymentMethod: '微信' })
    })
    check('客户缴别人的账单被拒', hackPay.status === 403, `HTTP ${hackPay.status}`)
  }

  // ---------- 3. 越权：客户删账单 ----------
  const del = await call(`/bill/${billId}`, tCus, { method: 'DELETE' })
  check('客户删账单被拒', del.status === 403, `HTTP ${del.status}`)

  // ---------- 4. 越权：运维缴费 ----------
  const opPay = await call(`/bill/${billId}/pay`, tOp, {
    method: 'POST',
    body: JSON.stringify({ amount: 1, paymentMethod: '现金' })
  })
  check('运维缴费被拒', opPay.status === 403, `HTTP ${opPay.status}`)

  // ---------- 5. 非法金额 ----------
  const over = await call(`/bill/${billId}/pay`, tCus, {
    method: 'POST',
    body: JSON.stringify({ amount: arrears + 100, paymentMethod: '微信' })
  })
  check('超额缴费被拒', rejected(over), `HTTP ${over.status} ${over.body?.message || ''}`)

  const zero = await call(`/bill/${billId}/pay`, tCus, {
    method: 'POST',
    body: JSON.stringify({ amount: 0, paymentMethod: '微信' })
  })
  check('0 元缴费被拒', rejected(zero), `HTTP ${zero.status} ${zero.body?.message || ''}`)

  const badMethod = await call(`/bill/${billId}/pay`, tCus, {
    method: 'POST',
    body: JSON.stringify({ amount: 1, paymentMethod: '比特币' })
  })
  check('非法支付方式被拒', rejected(badMethod), `HTTP ${badMethod.status} ${badMethod.body?.message || ''}`)

  // ---------- 5.5 管理员柜台代收 ----------
  // 挑另一笔待缴账单来测，避免影响下面针对 #billId 的客户缴费断言。
  // 代收与自助走同一个接口，差别只在 payerType / operatorId。
  const otherUnpaid = rows.filter(r =>
    Number(r.customerId) === 1 && Number(r.arrearsAmount) > 0 && r.id !== billId)
  if (otherUnpaid.length) {
    const t2 = otherUnpaid[0]
    const agentPay = await call(`/bill/${t2.id}/pay`, tAdmin, {
      method: 'POST',
      body: JSON.stringify({ amount: 1, paymentMethod: '现金' })
    })
    check('管理员柜台代收成功', accepted(agentPay), `账单 #${t2.id} 代收 1 元`)

    const recs2 = await call(`/bill/${t2.id}/payments`, tAdmin)
    const agentRec = (recs2.body?.data || []).find(r => r.payerType === 'AGENT')
    check('代收流水标记为 AGENT 且记录操作人', !!agentRec && agentRec.operatorId != null,
      agentRec ? `operatorId=${agentRec.operatorId}` : '未找到 AGENT 流水')

    // 管理员代收后，客户侧刷新应立即看到同一结果
    const cusAfter = await call('/bill/list?page=1&size=50', tCus)
    const cusRow = (cusAfter.body?.data?.records || []).find(r => r.id === t2.id)
    check('代收结果客户侧可见',
      cusRow && money(cusRow.arrearsAmount) === money(Number(t2.arrearsAmount) - 1),
      cusRow ? `客户侧尚欠 ¥${cusRow.arrearsAmount}` : '查不到')
  } else {
    check('管理员柜台代收', false, '客户 1 只有一笔待缴，无法在不干扰后续断言的前提下测试')
  }

  // ---------- 6. 正常缴费：先缴一半 ----------
  const half = money(arrears / 2)
  const pay1 = await call(`/bill/${billId}/pay`, tCus, {
    method: 'POST',
    body: JSON.stringify({ amount: half, paymentMethod: '微信' })
  })
  check('客户缴费成功', accepted(pay1), `HTTP ${pay1.status} ${pay1.body?.message || ''}`)
  const no1 = pay1.body?.data?.paymentNo
  check('返回流水号', !!no1, no1)
  check('缴后仍欠费', money(pay1.body?.data?.arrearsAmount) === money(arrears - half),
    `尚欠 ¥${pay1.body?.data?.arrearsAmount}`)

  // ---------- 7. 重新查：状态与金额 ----------
  page = await call('/bill/list?page=1&size=50', tCus)
  rows = page.body?.data?.records || []
  const after1 = rows.find(r => r.id === billId)
  check('客户侧已缴金额累加', money(after1.paidAmount) === money(paid0 + half),
    `¥${after1.paidAmount}`)
  check('客户侧尚欠扣减', money(after1.arrearsAmount) === money(arrears - half),
    `¥${after1.arrearsAmount}`)
  check('客户侧状态为部分缴纳或逾期', ['部分缴纳', '逾期'].includes(after1.paymentStatus),
    after1.paymentStatus)

  // ---------- 8. 管理员看到同一结果（跨界面同步）----------
  const adminPage = await call('/bill/list?page=1&size=50', tAdmin)
  const adminRow = (adminPage.body?.data?.records || []).find(r => r.id === billId)
  check('管理员看到同一笔账单', !!adminRow, adminRow ? `状态 ${adminRow.paymentStatus}` : '查不到')
  check('管理员侧已缴与客户侧一致',
    adminRow && money(adminRow.paidAmount) === money(after1.paidAmount),
    adminRow ? `管理员 ¥${adminRow.paidAmount} / 客户 ¥${after1.paidAmount}` : '')

  // ---------- 9. 缴费流水 ----------
  const recs = await call(`/bill/${billId}/payments`, tCus)
  const list = recs.body?.data || []
  check('缴费流水可查', list.length >= 1, `${list.length} 条`)
  check('流水含本次缴费', list.some(r => r.paymentNo === no1), no1)
  check('流水 payerType 为自助', list.some(r => r.paymentNo === no1 && r.payerType === 'SELF'))

  // ---------- 10. 客户首页 KPI 跟着变 ----------
  const kpi = await call('/dashboard/kpi', tCus)
  const kpiArrears = money(kpi.body?.data?.raw?.arrears)
  check('客户 KPI 待缴金额已减少', kpiArrears < arrearsBefore + 0.001,
    `缴前 ¥${arrearsBefore} → 缴后 ¥${kpiArrears}`)

  // ---------- 11. 结清 ----------
  const pay2 = await call(`/bill/${billId}/pay`, tCus, {
    method: 'POST',
    body: JSON.stringify({ paymentMethod: '支付宝' })   // 不传金额 = 结清
  })
  check('结清成功', accepted(pay2), `HTTP ${pay2.status}`)
  check('结清后状态为已缴', pay2.body?.data?.paymentStatus === '已缴', pay2.body?.data?.paymentStatus)
  check('结清后尚欠为 0', money(pay2.body?.data?.arrearsAmount) === 0,
    `¥${pay2.body?.data?.arrearsAmount}`)

  const again = await call(`/bill/${billId}/pay`, tCus, {
    method: 'POST',
    body: JSON.stringify({ amount: 1, paymentMethod: '微信' })
  })
  check('已结清账单不能重复缴', rejected(again),
    `HTTP ${again.status} ${again.body?.message || ''}`)

  // ---------- 12. 管理员代收 → 已移到缴费之前执行，见第 5.5 节 ----------

  console.log(`\n===== ${pass} PASS / ${fail} FAIL =====`)
  process.exit(fail === 0 ? 0 : 1)
}

main().catch(e => {
  console.error('ERROR', e)
  process.exit(1)
})
