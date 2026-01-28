<script setup lang="ts">
import HeaderNav from './components/HeaderNav.vue'
</script>

<template>
  <div class="app-root">
    <HeaderNav />
    <main class="page-main">
      <router-view />
    </main>
    <footer class="site-footer">
      <div class="footer-top">
        <div class="footer-top-inner">
          <div>
            <h3>关于 TravelMatch</h3>
            <p>
              TravelMatch 是面向年轻人的智能旅行助手，集路线规划、结伴找伴与社区分享于一体，
              为用户提供从灵感获取、行程设计到旅友匹配的完整体验，适合作为毕业设计展示的前端项目。
            </p>
          </div>
          <div>
            <h3>联系我们</h3>
            <p>邮箱：contact@travelmatch.com</p>
            <p>合作：biz@travelmatch.com</p>
          </div>
          <div>
            <h3>友情链接</h3>
            <p>旅游局官网 · 航空公司 · 酒店合作平台</p>
          </div>
        </div>
      </div>
      <div class="footer-bottom">
        © {{ new Date().getFullYear() }} TravelMatch 个性化旅游路线规划与旅友结伴平台
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-root {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.page-main {
  flex: 1;
}

.site-footer {
  margin-top: 24px;
  background: #020617;
  color: #e5e7eb;
  font-size: 13px;
}

.footer-top {
  border-top: 1px solid #0f172a;
  border-bottom: 1px solid #0f172a;
}

.footer-top-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 16px 28px;
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1.2fr) minmax(0, 1.4fr);
  gap: 20px;
}

.footer-top-inner h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #f9fafb;
}

.footer-top-inner p {
  margin: 0 0 4px;
  line-height: 1.6;
}

.footer-bottom {
  max-width: 1100px;
  margin: 0 auto;
  padding: 10px 16px 14px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

@media (max-width: 768px) {
  .footer-top-inner {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
