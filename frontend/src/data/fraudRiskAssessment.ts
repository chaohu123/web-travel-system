/**
 * 防诈骗风险测评 - 题目与维度数据
 */

export type QuestionType = 'single' | 'multiple'

export interface QuestionOption {
  text: string
  value: string
  score: number // 风险分数，越高越危险
}

export interface Question {
  id: number
  title: string
  type: QuestionType
  dimension: DimensionKey
  options: QuestionOption[]
}

export type DimensionKey = 'info' | 'fund' | 'link' | 'identity' | 'psychology'

export interface DimensionConfig {
  key: DimensionKey
  name: string
  description: string
}

export const DIMENSIONS: DimensionConfig[] = [
  { key: 'info', name: '信息防护', description: '个人信息与隐私保护意识' },
  { key: 'fund', name: '资金安全', description: '转账、汇款等资金操作警觉度' },
  { key: 'link', name: '链接辨别', description: '对陌生链接与钓鱼页面的识别' },
  { key: 'identity', name: '身份核实', description: '对对方身份的核实验证习惯' },
  { key: 'psychology', name: '心理防范', description: '对恐吓、利诱等话术的抵抗力' },
]

export const QUESTIONS: Question[] = [
  {
    id: 1,
    title: '您是否曾在不明网站或 App 中填写过身份证号、银行卡号、验证码等敏感信息？',
    type: 'single',
    dimension: 'info',
    options: [
      { text: '从未有过', value: 'a', score: 0 },
      { text: '偶尔、且多为正规渠道', value: 'b', score: 2 },
      { text: '有过几次，不确定是否安全', value: 'c', score: 5 },
      { text: '经常需要填写，不太区分场景', value: 'd', score: 8 },
    ],
  },
  {
    id: 2,
    title: '收到“客服/公安/法院”等来电要求转账、验证资金或下载指定 App 时，您会？',
    type: 'single',
    dimension: 'identity',
    options: [
      { text: '挂断后自行通过官方渠道核实', value: 'a', score: 0 },
      { text: '半信半疑，会查一下但可能照做', value: 'b', score: 4 },
      { text: '对方说得严重时会按提示操作', value: 'c', score: 7 },
      { text: '一般会配合“核实身份”或转账', value: 'd', score: 10 },
    ],
  },
  {
    id: 3,
    title: '对于短信、邮件或聊天中的链接，您通常会？',
    type: 'single',
    dimension: 'link',
    options: [
      { text: '不点陌生链接，有需要从官方入口进', value: 'a', score: 0 },
      { text: '会看一下链接域名再决定', value: 'b', score: 2 },
      { text: '有时会点，好奇或觉得方便', value: 'c', score: 5 },
      { text: '经常点开，没太在意来源', value: 'd', score: 8 },
    ],
  },
  {
    id: 4,
    title: '以下哪些情况您曾遇到过或可能按对方要求做过？（可多选）',
    type: 'multiple',
    dimension: 'fund',
    options: [
      { text: '从未遇到过，也不会按要求做', value: 'none', score: 0 },
      { text: '对方称中奖/退税，要求先交手续费或保证金', value: 'tax', score: 5 },
      { text: '对方称可刷单兼职，需要自己先垫付', value: 'task', score: 6 },
      { text: '对方称亲友出事急需汇款', value: 'urgent', score: 7 },
      { text: '对方要求在“安全账户”或指定 App 内操作资金', value: 'safe', score: 8 },
    ],
  },
  {
    id: 5,
    title: '当陌生人以“高收益、稳赚”等话术推荐投资、理财或赌博时，您的态度是？',
    type: 'single',
    dimension: 'psychology',
    options: [
      { text: '不信，知道多为诈骗', value: 'a', score: 0 },
      { text: '理性看待，会查证再决定', value: 'b', score: 2 },
      { text: '有点心动，可能会试试', value: 'c', score: 6 },
      { text: '愿意尝试，觉得机会难得', value: 'd', score: 9 },
    ],
  },
  {
    id: 6,
    title: '您是否会定期修改重要账号密码，并避免在多个平台使用同一密码？',
    type: 'single',
    dimension: 'info',
    options: [
      { text: '会，且使用强密码或密码管理器', value: 'a', score: 0 },
      { text: '重要账号会，其他有时会重复', value: 'b', score: 2 },
      { text: '偶尔改，密码较简单或重复使用', value: 'c', score: 5 },
      { text: '很少改，怕忘记', value: 'd', score: 7 },
    ],
  },
  {
    id: 7,
    title: '在转账或支付前，您是否会再次确认收款人、金额与用途？',
    type: 'single',
    dimension: 'fund',
    options: [
      { text: '每次都会仔细核对', value: 'a', score: 0 },
      { text: '大额会核，小额有时会疏忽', value: 'b', score: 2 },
      { text: '偶尔会核，时间紧时可能直接转', value: 'c', score: 5 },
      { text: '多为熟人或平台提示，很少主动核', value: 'd', score: 7 },
    ],
  },
  {
    id: 8,
    title: '看到“点击领取”“限时优惠”“账号异常请验证”等链接或弹窗时，您通常会？',
    type: 'single',
    dimension: 'link',
    options: [
      { text: '不点击，从官方 App/网站自行查看', value: 'a', score: 0 },
      { text: '先看来源再决定', value: 'b', score: 3 },
      { text: '有时会点进去看看', value: 'c', score: 6 },
      { text: '经常点，怕错过优惠或“出问题”', value: 'd', score: 9 },
    ],
  },
  {
    id: 9,
    title: '当对方以“保密”“不能告诉家人或警察”为由要求您单独操作时，您会？',
    type: 'single',
    dimension: 'psychology',
    options: [
      { text: '提高警惕，必要时咨询亲友或报警', value: 'a', score: 0 },
      { text: '会犹豫，但可能还是会照做', value: 'b', score: 5 },
      { text: '担心出事，倾向于按对方说的做', value: 'c', score: 8 },
      { text: '曾按对方要求瞒着家人操作', value: 'd', score: 10 },
    ],
  },
  {
    id: 10,
    title: '您认为以下哪些方式可以有效核实“客服/公检法”等身份？（可多选）',
    type: 'multiple',
    dimension: 'identity',
    options: [
      { text: '对方提供的电话回拨核实', value: 'callback', score: 4 },
      { text: '通过官网或官方 App 内客服入口咨询', value: 'official', score: 0 },
      { text: '拨打 110 或反诈专线 96110 咨询', value: 'police', score: 0 },
      { text: '对方发来的“工作证/立案文书”照片核实', value: 'photo', score: 6 },
    ],
  },
]

export const QUESTION_COUNT = QUESTIONS.length
export const ESTIMATED_MINUTES = Math.ceil(QUESTION_COUNT * 0.8)

/** 各维度满分（用于归一化雷达图与进度） */
export function getDimensionMaxScores(): Record<DimensionKey, number> {
  const max: Record<DimensionKey, number> = {
    info: 0,
    fund: 0,
    link: 0,
    identity: 0,
    psychology: 0,
  }
  for (const q of QUESTIONS) {
    if (q.type === 'single') {
      const top = Math.max(...q.options.map((o) => o.score))
      max[q.dimension] += top
    } else {
      const optionScores = q.options.filter((o) => o.value !== 'none').map((o) => o.score)
      max[q.dimension] += optionScores.reduce((a, b) => a + b, 0)
    }
  }
  return max
}

export type RiskLevel = 'low' | 'medium' | 'high'

export function getRiskLevel(totalScore: number): RiskLevel {
  if (totalScore <= 30) return 'low'
  if (totalScore <= 60) return 'medium'
  return 'high'
}

export const RISK_LEVEL_LABELS: Record<RiskLevel, string> = {
  low: '风险较低',
  medium: '存在风险',
  high: '风险较高',
}

export const RISK_LEVEL_COLORS: Record<RiskLevel, string> = {
  low: '#67c23a',
  medium: '#e6a23c',
  high: '#f56c6c',
}
