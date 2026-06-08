/**
 * 使用 fetch 读取 SSE（text/event-stream），按事件块解析 data: 行并回调。
 */
function emitSseBlock(raw, onMessage) {
    const text = raw.replace(/\r\n/g, '\n').replace(/\r/g, '\n')
    const lines = text.split('\n')
    let data = ''
    for (const line of lines) {
        if (line.startsWith('data:')) {
            const payload = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
            data += (data ? '\n' : '') + payload
        }
    }
    if (data) onMessage(data)
}

function extractCompleteBlocks(buffer, onMessage) {
    const normalized = buffer.replace(/\r\n/g, '\n')
    let rest = normalized
    const sep = '\n\n'
    let idx
    while ((idx = rest.indexOf(sep)) !== -1) {
        const block = rest.slice(0, idx)
        rest = rest.slice(idx + sep.length)
        if (block.trim()) emitSseBlock(block, onMessage)
    }
    return rest
}

/**
 * @param {string} url
 * @param {{ onMessage: (chunk: string) => void, signal?: AbortSignal }} options
 */
export async function fetchSse(url, {onMessage, signal}) {
    const res = await fetch(url, {
        signal,
        headers: {Accept: 'text/event-stream'},
    })

    if (!res.ok) {
        const text = await res.text().catch(() => '')
        throw new Error(text?.trim() || `请求失败 (${res.status})`)
    }

    const reader = res.body?.getReader()
    if (!reader) {
        throw new Error('无法读取响应流')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    try {
        while (true) {
            const {done, value} = await reader.read()
            if (value) {
                buffer += decoder.decode(value, {stream: true})
            }
            buffer = extractCompleteBlocks(buffer, onMessage)
            if (done) {
                if (buffer.trim()) emitSseBlock(buffer, onMessage)
                break
            }
        }
    } finally {
        reader.releaseLock()
    }
}
