import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import HomeView from './views/HomeView.vue'
import LoginView from './views/LoginView.vue'
import RegisterView from './views/RegisterView.vue'
import RouteCreateView from './views/RouteCreateView.vue'
import MyRoutesView from './views/MyRoutesView.vue'
import RouteDetailView from './views/RouteDetailView.vue'
import CompanionListView from './views/CompanionListView.vue'
import CompanionCreateView from './views/CompanionCreateView.vue'
import TeamDetailView from './views/TeamDetailView.vue'
import NotesListView from './views/NotesListView.vue'
import NoteCreateView from './views/NoteCreateView.vue'
import NoteDetailView from './views/NoteDetailView.vue'
import FeedView from './views/FeedView.vue'
import { useAuthStore } from './store'

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/register', name: 'register', component: RegisterView },
  { path: '/routes', name: 'routes', component: MyRoutesView },
  { path: '/routes/create', name: 'route-create', component: RouteCreateView },
  { path: '/routes/:id', name: 'route-detail', component: RouteDetailView },
  { path: '/companion', name: 'companion-list', component: CompanionListView },
  { path: '/companion/create', name: 'companion-create', component: CompanionCreateView },
  { path: '/teams/:id', name: 'team-detail', component: TeamDetailView },
  { path: '/notes', name: 'notes', component: NotesListView },
  { path: '/notes/create', name: 'note-create', component: NoteCreateView },
  { path: '/notes/:id', name: 'note-detail', component: NoteDetailView },
  { path: '/feed', name: 'feed', component: FeedView },
]

export const router = createRouter({
  history: createWebHistory(),
  routes,
})

const protectedPaths = ['/routes/create', '/companion', '/companion/create', '/notes/create', '/feed']

router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()
  if (!auth.token && protectedPaths.includes(to.path) && to.name !== 'login') {
    next({ name: 'login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

