export const EMOTION_META = {
  JOY: { label: '愉悦', color: '#2e9e6f', bg: '#e9f7f0', icon: '☀️' },
  CALM: { label: '平静', color: '#3b82a6', bg: '#ebf5fa', icon: '🌿' },
  ANXIETY: { label: '焦虑', color: '#d9822b', bg: '#fdf3e7', icon: '🌊' },
  SADNESS: { label: '悲伤', color: '#6b7aa1', bg: '#eff2f8', icon: '🌧️' },
  ANGER: { label: '愤怒', color: '#d45348', bg: '#fceeed', icon: '🔥' },
  MIXED: { label: '混合', color: '#8a63b8', bg: '#f4effa', icon: '🌗' },
  UNCERTAIN: { label: '不确定', color: '#7a8786', bg: '#f1f3f3', icon: '☁️' }
}

export const POLARITY_META = {
  POSITIVE: { label: '积极', type: 'success' },
  NEGATIVE: { label: '消极', type: 'danger' },
  NEUTRAL: { label: '中性', type: 'info' },
  MIXED: { label: '混合', type: 'warning' },
  UNCERTAIN: { label: '不确定', type: 'info' }
}

export const EMOTION_OPTIONS = Object.entries(EMOTION_META).map(([value, m]) => ({
  value,
  label: `${m.icon} ${m.label}`
}))

export function getEmotionMeta(code) {
  return EMOTION_META[(code || '').toUpperCase()] || EMOTION_META.UNCERTAIN
}

export function getPolarityMeta(code) {
  return POLARITY_META[(code || '').toUpperCase()] || POLARITY_META.UNCERTAIN
}
