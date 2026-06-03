import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
    baseURL: '/api',
    timeout: 60000
})

request.interceptors.request.use(config => {
    const token = localStorage.getItem('butler_token')

    if (token) {
        config.headers.Authorization = `Bearer ${token}`
    }

    return config
})

request.interceptors.response.use(
    response => {
        const res = response.data

        if (res.code === 200) {
            return res.data
        }

        if (res.code === 401) {
            ElMessage.error(res.msg || '登录已过期')
            localStorage.removeItem('butler_token')
            router.push('/login')
            return Promise.reject(res)
        }

        ElMessage.error(res.msg || '请求失败')
        return Promise.reject(res)
    },
    error => {
        ElMessage.error(error.message || '网络异常')
        return Promise.reject(error)
    }
)

export default request