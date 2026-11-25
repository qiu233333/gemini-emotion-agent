import http from './http'

export const api = {
  // System & Auth
  systemInfo: () => http.get('/system/info'),
  health: () => http.get('/health'),
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  getProfile: () => http.get('/user/profile'),
  updateProfile: (data) => http.put('/user/profile', data),
  clearMemory: () => http.post('/user/memory/clear'),

  // Diaries
  listDiaries: (params) => http.get('/diaries', { params }),
  getDiary: (id) => http.get(`/diaries/${id}`),
  createDiary: (data) => http.post('/diaries', data),
  updateDiary: (id, data) => http.put(`/diaries/${id}`, data),
  correctDiary: (id, data) => http.patch(`/diaries/${id}/correct`, data),
  reanalyzeDiary: (id, method) => http.post(`/diaries/${id}/reanalyze`, null, { params: method ? { method } : {} }),
  regenerateSuggestion: (id, params) => http.post(`/diaries/${id}/regenerate-suggestion`, null, { params }),
  deleteDiary: (id) => http.delete(`/diaries/${id}`),

  // Stats & Calendar
  statsDashboard: (days = 30) => http.get('/stats/dashboard', { params: { days } }),
  statsCalendar: (month) => http.get('/stats/calendar', { params: month ? { month } : {} }),

  // NLP Experiment Compare
  nlpAnalyze: (data) => http.post('/nlp/analyze', data),

  // Chat Companion
  listChatSessions: () => http.get('/chat/sessions'),
  createChatSession: (data) => http.post('/chat/sessions', data),
  listChatMessages: (id) => http.get(`/chat/sessions/${id}/messages`),
  sendChatMessage: (id, content) => http.post(`/chat/sessions/${id}/messages`, { content }),
  deleteChatSession: (id) => http.delete(`/chat/sessions/${id}`),

  // Periodic Reports
  listReports: () => http.get('/reports'),
  generateReport: (type = 'WEEKLY') => http.post('/reports/generate', null, { params: { type } }),
  deleteReport: (id) => http.delete(`/reports/${id}`),

  // User Feedback
  submitFeedback: (data) => http.post('/feedback', data),
  myFeedback: () => http.get('/feedback/mine'),

  // Admin
  adminOverview: () => http.get('/admin/overview'),
  adminUsers: (keyword) => http.get('/admin/users', { params: keyword ? { keyword } : {} }),
  adminUpdateUserStatus: (id, data) => http.patch(`/admin/users/${id}/status`, data),
  adminRiskRules: () => http.get('/admin/risk-rules'),
  adminCreateRiskRule: (data) => http.post('/admin/risk-rules', data),
  adminUpdateRiskRule: (id, data) => http.put(`/admin/risk-rules/${id}`, data),
  adminDeleteRiskRule: (id) => http.delete(`/admin/risk-rules/${id}`),
  adminFeedback: (status) => http.get('/admin/feedback', { params: status ? { status } : {} }),
  adminResolveFeedback: (id, adminReply) => http.patch(`/admin/feedback/${id}/resolve`, { adminReply }),
  adminAuditLogs: (limit = 60) => http.get('/admin/audit-logs', { params: { limit } })
}
