<script setup>
import {computed, onMounted} from 'vue'
import {useRoute} from 'vue-router'
import AppHeader from './components/AppHeader.vue'
import {useAuthStore} from './stores/auth.js'

const auth = useAuthStore()
const route = useRoute()
const showGlobalHeader = computed(() => route.name !== 'home')
const mainClassName = computed(() => (showGlobalHeader.value ? 'app-main' : 'app-main app-main-home'))

onMounted(() => {
  auth.ensureAuthLoaded()
})
</script>

<template>
  <div class="app-shell">
    <AppHeader v-if="showGlobalHeader"/>
    <main :class="mainClassName">
      <router-view/>
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

.app-main-home {
  min-height: 100vh;
}
</style>
