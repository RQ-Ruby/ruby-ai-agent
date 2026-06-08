import {http} from './http.js'

export const USER_ROLE_OPTIONS = [
    {label: '普通用户', value: 'user'},
    {label: '管理员', value: 'admin'},
    {label: '封禁用户', value: 'ban'},
]

function unwrapResponse(response) {
    const body = response?.data
    if (!body || typeof body !== 'object' || !('code' in body)) {
        return body
    }
    if (body.code !== 0) {
        throw new Error(body.message || '请求失败')
    }
    return body.data
}

export async function userRegister(payload) {
    const response = await http.post('/user/register', payload)
    return unwrapResponse(response)
}

export async function userLogin(payload) {
    const response = await http.post('/user/login', payload)
    return unwrapResponse(response)
}

export async function getLoginUser() {
    const response = await http.get('/user/get/login')
    return unwrapResponse(response)
}

export async function userLogout() {
    const response = await http.post('/user/logout')
    return unwrapResponse(response)
}

export async function listUsersByPage(payload) {
    const response = await http.post('/user/list/page/vo', payload)
    return unwrapResponse(response)
}

export async function addUser(payload) {
    const response = await http.post('/user/add', payload)
    return unwrapResponse(response)
}

export async function updateUser(payload) {
    const response = await http.post('/user/update', payload)
    return unwrapResponse(response)
}

export async function deleteUser(id) {
    const response = await http.post('/user/delete', {id})
    return unwrapResponse(response)
}
