import { createRouter, createWebHistory } from 'vue-router'

import Login from '../views/Login.vue'
import Chat from '../views/Chat.vue'

const routes = [
    {
        path: '/',
        redirect: '/chat'
    },
    {
        path: '/login',
        component: Login
    },
    {
        path: '/chat',
        component: Chat
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

router.beforeEach((to, from, next) => {
    const token = localStorage.getItem('butler_token')

    if (to.path !== '/login' && !token) {
        next('/login')
        return
    }

    next()
})

export default router