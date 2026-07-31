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
 * The error bodies of this API are flat {field: message} maps (see GlobalExceptionHandler), so the
 * values alone read as a sentence. Anything else falls back to the status line.
 */
async function errorMessage(response) {
  try {
    const body = await response.json()
    const messages = Object.values(body).filter((v) => typeof v === 'string')
    if (messages.length > 0) {
      return messages.join(' · ')
    }
  } catch {
    // not JSON — fall through to the generic message
  }
  if (response.status === 401) {
    return 'Not authenticated — reload the page to enter your credentials.'
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
