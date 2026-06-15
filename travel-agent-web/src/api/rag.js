import {http} from './http.js'

export function listRagDocuments(data) {
    return http.post('/ai/rag/document/page', data).then((res) => res.data?.data)
}

export function saveRagDocument(data) {
    return http.post('/ai/rag/document/save', data).then((res) => res.data?.data)
}

export function deleteRagDocument(id) {
    return http.post('/ai/rag/document/delete', null, {params: {id}}).then((res) => res.data?.data)
}

export function refreshRagVectorDb() {
    return http.post('/ai/rag/vector/refresh').then((res) => res.data?.data)
}
