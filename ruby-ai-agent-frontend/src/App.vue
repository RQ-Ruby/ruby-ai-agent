<script setup>
import { onMounted } from 'vue'
import AppHeader from './components/AppHeader.vue'
import { useAuthStore } from './stores/auth.js'

const auth = useAuthStore()

onMounted(() => {
  auth.ensureAuthLoaded()
})
</script>

<template>
  <div class="app-shell">
    <AppHeader />
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-main {
  min-height: calc(100vh - 92px);
}
</style>
