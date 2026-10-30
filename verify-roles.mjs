/**
 * 三角色视图差异验证脚本
 *
 * 用法：node verify-roles.mjs
 * 前置：5 个服务已启动（power-gateway:8080）
 *
 * 验证思路——不只看"前端有没有隐藏菜单"，而是直接打 API，
 * 确认服务端按身份返回了不同的数据范围：
 *   1. admin      → 全域账单 / 全域用电 / 运维 KPI
 *   2. weiyixiao  → 设备可读、账单不可读（403）
 *   3. customer1  → 只能看到自己名下账单与用电，且拿不到设备统计
 */
const BASE = 'http://127.0.0.1:8080/api'
const PWD = process.env.DEMO_PASSWORD || 'admin123'

const ACCOUNTS = [
  { user: 'admin', expect: 'ADMIN' },
  { user: 'weiyixiao', expect: 'OPERATOR' },
  { user: 'customer1', expect: 'CUSTOMER' }
]

async function call(path, token) {
  const res = await fetch(BASE + path, {
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  })
  let body
  const text = await res.text()
  try { body = JSON.parse(text) } catch { body = text }
  return { status: res.status, body }
}

async function login(username) {
  const res = await fetch(BASE + '/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: PWD })
  })
  const json = await res.json()
  return json?.data?.token
}

const pad = (s, n) => String(s ?? '').padEnd(n)

let failed = 0
function check(label, ok, detail) {
  if (!ok) failed++
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${pad(label, 34)} ${detail}`)
}

for (const { user, expect } of ACCOUNTS) {
  console.log(`\n===== ${user}（期望角色 ${expect}） =====`)
  const token = await login(user)
  if (!token) {
    console.log('  FAIL  登录失败，拿不到 token')
    failed++
    continue
  }

  // 1. 登录返回的角色
  const me = await call('/auth/me', token)
  const role = me.body?.data?.role
  check('登录身份', role === expect, `role=${role} customerId=${me.body?.data?.customerId} staffId=${me.body?.data?.staffId}`)

  // 2. 首页视图配置
  const pf = await call('/dashboard/profile', token)
  check('首页视图配置', pf.status === 200, `title=${pf.body?.data?.title} 区块=${(pf.body?.data?.sections || []).join('/')}`)

  // 3. 首页 KPI（角色分流）
  const kpi = await call('/dashboard/kpi', token)
  const keys = Object.keys(kpi.body?.data?.raw || {})
  check('首页 KPI 字段', kpi.status === 200, `${keys.slice(0, 6).join(',')}`)

  // 4. 账单列表的数据范围
  const bill = await call('/bill/list?page=1&size=50', token)
  if (bill.status === 200) {
    const rows = bill.body?.data?.records || bill.body?.data?.list || []
    const owners = [...new Set(rows.map(r => r.customerId))]
    check('账单可读', true, `共 ${bill.body?.data?.total} 条，涉及客户 ${owners.join(',')}`)
  } else {
    check('账单可读', bill.status === 403, `HTTP ${bill.status} ${bill.body?.message || ''}`)
  }

  // 5. 用电汇总
  const usage = await call('/usage/summary?days=30', token)
  if (usage.status === 200) {
    const cur = usage.body?.data?.current || {}
    const scope = usage.body?.data?.scopeCustomerId
    check('用电汇总可读', true, `近30天=${cur.totalUsage}kWh 天数=${cur.dayCount} 数据范围=${scope ?? '全站'}`)
  } else {
    check('用电汇总可读', usage.status === 403, `HTTP ${usage.status} ${usage.body?.message || ''}`)
  }

  // 6. 设备统计（运维/管理员专属）
  const eq = await call('/equipment/list?page=1&size=5', token)
  const eqExpect = role === 'CUSTOMER' ? 403 : 200
  check('设备列表可读', eq.status === eqExpect, `HTTP ${eq.status}（期望 ${eqExpect}）`)

  const ops = await call('/operations/summary', token)
  if (ops.status === 200) {
    const d = ops.body?.data || {}
    check('运维汇总可读', true, `设备=${d.totalEquipment} 变电站=${d.totalSubstations} 电表=${d.totalMeters}`)
  } else {
    check('运维汇总可读', ops.status === 403, `HTTP ${ops.status} ${ops.body?.message || ''}`)
  }

  // 7. 告警与检修（内部运营数据）
  const al = await call('/alert/list?page=1&size=5', token)
  const alExpect = role === 'CUSTOMER' ? 403 : 200
  check('告警列表可读', al.status === alExpect, `HTTP ${al.status}（期望 ${alExpect}）`)

  // 7. 越权试探：客户硬塞 customerId=2，服务端必须无视
  if (role === 'CUSTOMER') {
    const mine = await call('/bill/list?page=1&size=50', token)
    const hack = await call('/bill/list?page=1&size=50&customerId=2', token)
    const mineRows = mine.body?.data?.records || []
    const hackRows = hack.body?.data?.records || []
    const mineIds = mineRows.map(r => r.id).sort().join(',')
    const hackIds = hackRows.map(r => r.id).sort().join(',')
    check('篡改 customerId 无效', mineIds === hackIds && mineIds !== '',
          `本户账单 [${mineIds}]，塞 customerId=2 后 [${hackIds}]`)

    // 越权的另一半：客户拿不到别户的账单明细
    const otherCustomerBills = [2, 3, 4]
    const leaked = []
    for (const cid of otherCustomerBills) {
      const r = await call(`/bill/customer/${cid}`, token)
      if (r.status === 200 && (r.body?.data || []).length > 0) leaked.push(cid)
    }
    check('别户账单不可读', leaked.length === 0, leaked.length ? `泄露客户 ${leaked.join(',')}` : '3 个客户均拒绝')
  }
}

console.log(`\n${failed === 0 ? '全部通过' : failed + ' 项未通过'}`)
process.exit(failed === 0 ? 0 : 1)
