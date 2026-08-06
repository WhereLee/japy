<template>
  <router-view />
</template>

<script setup lang="ts"></script>

<style>
/* ============ 设计 token：书卷气"纸上图书馆" ============ */
:root {
  --paper: #f7f3ea;          /* 宣纸底 */
  --paper-deep: #efe8da;     /* 深一层的纸面 */
  --ink: #2d2a26;            /* 墨色主文字 */
  --ink-soft: #5c564c;       /* 次级文字 */
  --ink-faint: #8a8378;      /* 弱文字/元信息 */
  --cinnabar: #b3472d;       /* 朱砂强调 */
  --cinnabar-deep: #93351f;
  --line: #e2dac8;           /* 分隔线 */
  --serif: 'Noto Serif SC', 'Songti SC', 'SimSun', serif;
  --sans: 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
}
body {
  font-family: var(--sans);
  background: var(--paper);
  color: var(--ink);
  -webkit-font-smoothing: antialiased;
}
h1, h2, h3, .serif {
  font-family: var(--serif);
}
</style>
