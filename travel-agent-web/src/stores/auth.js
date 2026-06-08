import {computed, reactive, readonly} from 'vue'
import {getLoginUser, userLogout} from '../api/user.js'

const state = reactive({
    loading: false,
    initialized: false,
    loginUser: null,
})

async function fetchLoginUser() {
    state.loading = true
    try {
        const user = await getLoginUser()
        state.loginUser = user || null
        return state.loginUser
    } catch {
        state.loginUser = null
        return null
    } finally {
        state.loading = false
        state.initialized = true
    }
}

async function ensureAuthLoaded() {
    if (state.initialized) {
        return state.loginUser
    }
    return fetchLoginUser()
}

async function logout() {
    try {
        await userLogout()
    } catch {
    } finally {
        state.loginUser = null
        state.initialized = true
    }
}

function setLoginUser(user) {
    state.loginUser = user || null
    state.initialized = true
}

export function useAuthStore() {
    return {
        state: readonly(state),
        isLoggedIn: computed(() => Boolean(state.loginUser?.id)),
        isAdmin: computed(() => state.loginUser?.userRole === 'admin'),
        fetchLoginUser,
        ensureAuthLoaded,
        logout,
        setLoginUser,
    }
}
