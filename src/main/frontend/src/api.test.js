import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, deleteProposal, listProposals, saveEntries } from './api.js'

function mockFetch(response) {
  const fetchMock = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const ok = (body, status = 200) => ({
  ok: true,
  status,
  statusText: 'OK',
  json: async () => body,
})

const failure = (status, body) => ({
  ok: false,
  status,
  statusText: 'Error',
  json: async () => {
    if (body === undefined) throw new Error('not json')
    return body
  },
})

describe('api', () => {
  beforeEach(() => vi.unstubAllGlobals())

  it('returns the parsed body', async () => {
    mockFetch(ok([{ id: 1 }]))
    await expect(listProposals()).resolves.toEqual([{ id: 1 }])
  })

  it('sends a JSON body only when there is one', async () => {
    const fetchMock = mockFetch(ok({ id: 1 }))
    await saveEntries(1, [{ minutes: 30, note: 'x' }])

    const [path, init] = fetchMock.mock.calls[0]
    expect(path).toBe('/proposals/1/entries')
    expect(init.method).toBe('PUT')
    expect(init.headers['Content-Type']).toBe('application/json')
    expect(JSON.parse(init.body)).toEqual({ entries: [{ minutes: 30, note: 'x' }] })
  })

  it('does not try to parse a 204', async () => {
    mockFetch({ ok: true, status: 204, json: async () => expect.unreachable() })
    await expect(deleteProposal(1)).resolves.toBeNull()
  })

  it('turns the flat error map into one message', async () => {
    mockFetch(failure(409, { status: 'proposal 1 is BOOKED, expected DRAFT' }))
    await expect(saveEntries(1, [])).rejects.toThrow('proposal 1 is BOOKED, expected DRAFT')
  })

  it('joins several field errors', async () => {
    mockFetch(failure(400, { entries: 'must not be empty', date: 'is required' }))
    await expect(saveEntries(1, [])).rejects.toThrow('must not be empty · is required')
  })

  it('explains a 401 rather than showing an empty body', async () => {
    mockFetch(failure(401))
    await expect(listProposals()).rejects.toThrow(/Not authenticated/)
  })

  it('explains a 401 even when it arrives as Boot error JSON', async () => {
    // The entry point sends the 401 through /error, so a body is present — the status has to be
    // checked first or this message would never be reached.
    mockFetch(
      failure(401, {
        timestamp: '2026-08-01T07:00:00.000+00:00',
        status: 401,
        error: 'Unauthorized',
        path: '/proposals',
      }),
    )
    await expect(listProposals()).rejects.toThrow(/Not authenticated/)
  })

  it('does not read Boot error bodies as a field map', async () => {
    // Joining the values would show a timestamp and a path instead of a cause.
    mockFetch(
      failure(500, {
        timestamp: '2026-08-01T07:00:00.000+00:00',
        status: 500,
        error: 'Internal Server Error',
        path: '/proposals/default/2026-13-99',
      }),
    )
    await expect(listProposals()).rejects.toThrow('500 Internal Server Error')
  })

  it('falls back to the status line for a non-JSON body', async () => {
    mockFetch(failure(502))
    await expect(listProposals()).rejects.toThrow('502 Error')
  })

  it('carries the status on the error', async () => {
    mockFetch(failure(404, { proposal: 'unknown' }))
    await expect(listProposals()).rejects.toMatchObject({ name: 'ApiError', status: 404 })
    expect(new ApiError('x', 500).status).toBe(500)
  })
})
