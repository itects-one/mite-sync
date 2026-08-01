/**
 * Thin wrapper around the mite-sync REST API.
 *
 * Authentication is HTTP basic and handled by the browser: the first request answers 401 with a
 * WWW-Authenticate header, the browser prompts and repeats the credentials on every later request.
 * There is nothing to store here — and consequently no way to log out other than closing the
 * browser.
 */

/** An API call that came back with a non-2xx status. */
export class ApiError extends Error {
  constructor(message, status) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

/**
 * Only the exceptions GlobalExceptionHandler knows about produce a flat {field: message} map.
 * Everything else — an unparseable date, a failing Mite call, an expired token — falls through to
 * Boot's own error body, which carries a timestamp and a path but no cause. Telling the two apart
 * matters: joining the values of the latter shows the user a timestamp instead of a reason.
 */
function fieldMessages(body) {
  if (body === null || typeof body !== 'object' || Array.isArray(body)) {
    return null
  }
  if ('timestamp' in body || 'path' in body) {
    return null
  }
  const messages = Object.values(body).filter((v) => typeof v === 'string')
  return messages.length > 0 ? messages.join(' · ') : null
}

async function errorMessage(response) {
  // Checked before the body: a 401 is answered by the entry point and dispatched through /error,
  // so it arrives as Boot's generic body and would otherwise never reach this message.
  if (response.status === 401) {
    return 'Not authenticated — reload the page to enter your credentials.'
  }

  let body = null
  try {
    body = await response.json()
  } catch {
    // not JSON — fall through to the status line
  }

  const fields = fieldMessages(body)
  if (fields !== null) {
    return fields
  }
  if (body !== null && typeof body.error === 'string') {
    return `${body.status ?? response.status} ${body.error}`
  }
  return `${response.status} ${response.statusText}`
}

async function request(method, path, body) {
  const response = await fetch(path, {
    method,
    headers: body === undefined ? {} : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    throw new ApiError(await errorMessage(response), response.status)
  }
  return response.status === 204 ? null : response.json()
}

export const listProfiles = () => request('GET', '/profiles')

export const listProposals = () => request('GET', '/proposals')

export const getProposal = (id) => request('GET', `/proposals/${id}`)

export const generateProposal = (profileKey, date, assignment) =>
  request('POST', `/proposals/${profileKey}/${date}`, assignment)

export const saveEntries = (id, entries) =>
  request('PUT', `/proposals/${id}/entries`, { entries })

export const confirmProposal = (id) => request('POST', `/proposals/${id}/confirm`)

export const deleteProposal = (id) => request('DELETE', `/proposals/${id}`)
