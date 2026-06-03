import request from '../utils/request'

export function listNotifications() {
    return request({
        url: '/notification/list',
        method: 'get'
    })
}

export function countUnreadNotifications() {
    return request({
        url: '/notification/unread/count',
        method: 'get'
    })
}

export function markNotificationRead(notificationId) {
    return request({
        url: `/notification/${notificationId}/read`,
        method: 'put'
    })
}

export function markAllNotificationsRead() {
    return request({
        url: '/notification/read/all',
        method: 'put'
    })
}