import { ElNotification } from 'element-plus'

let socket = null
let reconnectTimer = null
let manualClose = false

export function connectNotificationSocket() {
    const token = localStorage.getItem('butler_token')

    if (!token) {
        return
    }

    if (socket && socket.readyState === WebSocket.OPEN) {
        return
    }

    manualClose = false

    const wsUrl = `ws://localhost:8181/ws/notification?token=${token}`

    socket = new WebSocket(wsUrl)

    socket.onopen = () => {
        console.log('WebSocket 通知连接成功')
    }

    socket.onmessage = (event) => {
        console.log('收到 WebSocket 通知：', event.data)

        try {
            const data = JSON.parse(event.data)

            if (data.type === 'BUDGET_WARNING') {
                ElNotification({
                    title: '预算预警',
                    message: data.content || '你的预算状态发生变化',
                    type: data.level === 'OVER' ? 'error' : 'warning',
                    duration: 6000
                })

                return
            }

            ElNotification({
                title: '系统通知',
                message: data.content || event.data,
                type: 'info',
                duration: 5000
            })
        } catch (e) {
            ElNotification({
                title: '系统通知',
                message: event.data,
                type: 'info',
                duration: 5000
            })
        }
    }

    socket.onerror = () => {
        console.log('WebSocket 通知连接异常')
    }

    socket.onclose = () => {
        console.log('WebSocket 通知连接关闭')

        if (!manualClose) {
            reconnectTimer = setTimeout(() => {
                connectNotificationSocket()
            }, 3000)
        }
    }
}

export function closeNotificationSocket() {
    manualClose = true

    if (reconnectTimer) {
        clearTimeout(reconnectTimer)
        reconnectTimer = null
    }

    if (socket) {
        socket.close()
        socket = null
    }
}