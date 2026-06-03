import request from '../utils/request'

export function chat(data) {
    return request({
        url: '/agent/chat',
        method: 'post',
        data
    })
}

export function listSessions() {
    return request({
        url: '/agent/sessions',
        method: 'get'
    })
}

export function getHistory(sessionId) {
    return request({
        url: `/agent/history/${sessionId}`,
        method: 'get'
    })
}
export function deleteSession(sessionId) {
    return request({
        url: `/agent/session/${sessionId}`,
        method: 'delete'
    })
}
