import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import Home from '../views/Home.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import MyRoutesView from '../views/MyRoutesView.vue'
import RoutePlanningView from '../views/RoutePlanningView.vue'
import RouteDetailView from '../views/RouteDetailView.vue'
import SpotDetailView from '../views/SpotDetailView.vue'
import CompanionListView from '../views/CompanionListView.vue'
import CompanionDetailView from '../views/CompanionDetailView.vue'
import CompanionCreateView from '../views/CompanionCreateView.vue'
import TeamDetailView from '../views/TeamDetailView.vue'
import NotesListView from '../views/NotesListView.vue'
import NoteCreateView from '../views/NoteCreateView.vue'
import NoteDetailView from '../views/NoteDetailView.vue'
import FeedView from '../views/FeedView.vue'
import CommunityView from '../views/CommunityView.vue'
import ProfileView from '../views/ProfileView.vue'
import { useAuthStore } from '../store'

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: Home },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/routes', name: 'routes', component: MyRoutesView },
  { path: '/routes/create', name: 'route-create', component: RoutePlanningView },
  { path: '/routes/:id', name: 'route-detail', component: RouteDetailView },
  { path: '/spots/:id', name: 'spot-detail', component: SpotDetailView },
  { path: '/companion', name: 'companion-list', component: CompanionListView },
  { path: '/companion/create', name: 'companion-create', component: CompanionCreateView },
  { path: '/companion/:id', name: 'companion-detail', component: CompanionDetailView },
  { path: '/teams/:id', name: 'team-detail', component: TeamDetailView },
  { path: '/notes', name: 'notes', component: NotesListView },
  { path: '/notes/create', name: 'note-create', component: NoteCreateView },
  { path: '/notes/:id', name: 'note-detail', component: NoteDetailView },
  { path: '/feed', name: 'feed', component: FeedView },
  { path: '/community', name: 'community', component: CommunityView },
  { path: '/profile', name: 'profile', component: ProfileView, meta: { requiresAuth: true } },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 仅对「必须登录才能访问」的页面做路由守卫：创建类、个人中心。浏览类页面（首页/结伴列表/详情/社区/游记/路线列表等）可不登录访问。
const protectedPaths = ['/routes/create', '/companion/create', '/notes/create', '/profile']

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (!auth.token && protectedPaths.includes(to.path) && to.name !== 'login') {
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})
