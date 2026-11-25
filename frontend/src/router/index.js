import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', name: 'Dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
      { path: 'diaries', name: 'DiaryList', component: () => import('@/views/diary/DiaryListView.vue') },
      { path: 'diaries/new', name: 'DiaryNew', component: () => import('@/views/diary/DiaryEditorView.vue') },
      { path: 'diaries/:id/edit', name: 'DiaryEdit', component: () => import('@/views/diary/DiaryEditorView.vue') },
      { path: 'diaries/:id', name: 'DiaryDetail', component: () => import('@/views/diary/DiaryDetailView.vue') },
      { path: 'stats', name: 'Stats', component: () => import('@/views/stats/StatsView.vue') },
      { path: 'chat', name: 'Chat', component: () => import('@/views/chat/ChatView.vue') },
      { path: 'reports', name: 'Reports', component: () => import('@/views/report/ReportsView.vue') },
      { path: 'nlp-lab', name: 'NlpLab', component: () => import('@/views/nlp/NlpLabView.vue') },
      { path: 'profile', name: 'Profile', component: () => import('@/views/profile/ProfileView.vue') },
      {
        path: 'admin',
        name: 'Admin',
        component: () => import('@/views/admin/AdminView.vue'),
        meta: { requiresAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAuthenticated) {
    return '/login'
  }
  if (to.path === '/login' && auth.isAuthenticated) {
    return '/dashboard'
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return '/dashboard'
  }
})

export default router
