import request from '../utils/request'

export function sendSmsCode(data) {
    return request({
        url: '/sms/send',
        method: 'post',
        data
    })
}

export function loginBySms(data) {
    return request({
        url: '/auth/login/sms',
        method: 'post',
        data
    })
}

export function getCurrentUser() {
    return request({
        url: '/auth/me',
        method: 'get'
    })
}

export function logout() {
    return request({
        url: '/auth/logout',
        method: 'post'
    })
}