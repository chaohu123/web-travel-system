<script setup lang="ts">
</script>

<template>
  <section class="hero">
    <h1>个性化旅游路线规划 &amp; 旅友结伴平台</h1>
    <p class="text-subtle">根据你的兴趣与预算，一键生成智能行程，并找到志同道合的旅伴。</p>
    <div class="hero-actions">
      <button class="btn primary">开始规划行程</button>
      <button class="btn ghost">浏览结伴信息</button>
    </div>
  </section>
</template>

<style scoped>
.hero {
  max-width: 720px;
  margin: 0 auto;
  text-align: center;
}

.hero h1 {
  font-size: 36px;
  line-height: 1.2;
  margin-bottom: 12px;
}

.hero-actions {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-top: 24px;
}

@media (max-width: 768px) {
  .hero h1 {
    font-size: 26px;
  }

  .hero-actions {
    flex-direction: column;
  }
}
</style>

