<script setup lang="ts">
</script>

<template>
  <div class="app">
    <header class="app-header">
      <div class="logo">TravelMatch</div>
      <nav class="nav">
        <a href="#">路线规划</a>
        <a href="#">旅友结伴</a>
        <a href="#">游记社区</a>
        <a href="#">个人中心</a>
      </nav>
      <div class="auth">
        <button class="btn primary">登录</button>
        <button class="btn ghost">注册</button>
      </div>
    </header>
    <main class="app-main">
      <section class="hero">
        <h1>个性化旅游路线规划 &amp; 旅友结伴平台</h1>
        <p>根据你的兴趣与预算，一键生成智能行程，并找到志同道合的旅伴。</p>
        <div class="hero-actions">
          <button class="btn primary">开始规划行程</button>
          <button class="btn ghost">浏览结伴信息</button>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.app {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: linear-gradient(135deg, #f3f7ff, #eef9ff);
  color: #1f2933;
  font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 40px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
  position: sticky;
  top: 0;
  z-index: 10;
}

.logo {
  font-weight: 800;
  font-size: 20px;
  letter-spacing: 0.06em;
  color: #2563eb;
}

.nav {
  display: flex;
  gap: 24px;
  font-size: 14px;
}

.nav a {
  color: #4b5563;
  text-decoration: none;
  position: relative;
  padding-bottom: 4px;
}

.nav a::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: 0;
  width: 0;
  height: 2px;
  background: linear-gradient(90deg, #2563eb, #22c55e);
  transition: width 0.2s ease;
}

.nav a:hover::after {
  width: 100%;
}

.auth {
  display: flex;
  gap: 8px;
}

.btn {
  padding: 8px 16px;
  border-radius: 999px;
  border: none;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s ease;
}

.btn.primary {
  background: linear-gradient(135deg, #2563eb, #22c55e);
  color: #fff;
  box-shadow: 0 8px 16px rgba(37, 99, 235, 0.25);
}

.btn.primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(37, 99, 235, 0.35);
}

.btn.ghost {
  background: transparent;
  border: 1px solid #d1d5db;
  color: #4b5563;
}

.btn.ghost:hover {
  background: #f3f4f6;
}

.app-main {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px 24px;
}

.hero {
  max-width: 720px;
  text-align: center;
}

.hero h1 {
  font-size: 36px;
  line-height: 1.2;
  margin-bottom: 16px;
  color: #111827;
}

.hero p {
  font-size: 16px;
  color: #6b7280;
  margin-bottom: 24px;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
}

@media (max-width: 768px) {
  .app-header {
    padding: 12px 16px;
  }

  .nav {
    display: none;
  }

  .hero h1 {
    font-size: 26px;
  }

  .hero-actions {
    flex-direction: column;
  }
}
</style>

