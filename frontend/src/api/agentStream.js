export async function streamChat(data, callbacks = {}) {
    const token = localStorage.getItem('butler_token')

    if (!token) {
        throw new Error('未登录，请先登录')
    }

    const response = await fetch('/api/agent/chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${token}`
        },
        body: JSON.stringify(data)
    })

    if (!response.ok) {
        let errorText = ''

        try {
            errorText = await response.text()
        } catch (e) {
            errorText = ''
        }

        console.error('流式接口响应失败：', response.status, errorText)

        if (response.status === 401) {
            throw new Error('登录已过期，请重新登录')
        }

        throw new Error('流式请求失败，状态码：' + response.status)
    }

    if (!response.body) {
        throw new Error('当前浏览器不支持流式读取')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')

    let buffer = ''

    while (true) {
        const { value, done } = await reader.read()

        if (done) {
            break
        }

        buffer += decoder.decode(value, { stream: true })

        const blocks = buffer.split('\n\n')
        buffer = blocks.pop() || ''

        for (const block of blocks) {
            handleSseBlock(block, callbacks)
        }
    }

    if (buffer.trim()) {
        handleSseBlock(buffer, callbacks)
    }
}

function handleSseBlock(block, callbacks) {
    const lines = block.split('\n')

    let eventName = ''
    let data = ''

    for (const line of lines) {
        if (line.startsWith('event:')) {
            eventName = line.replace('event:', '').trim()
        }

        if (line.startsWith('data:')) {
            data += line.replace('data:', '')
        }
    }

    data = data.trim()

    if (!eventName) {
        return
    }

    if (eventName === 'session') {
        callbacks.onSession && callbacks.onSession(data)
        return
    }

    if (eventName === 'message') {
        callbacks.onMessage && callbacks.onMessage(data)
        return
    }

    if (eventName === 'done') {
        callbacks.onDone && callbacks.onDone(data)
        return
    }

    if (eventName === 'error') {
        callbacks.onError && callbacks.onError(data)
    }
}