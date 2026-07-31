import { useEffect, useState } from 'react'

/**
 * Routing over the URL hash. Deep links work without the server knowing any of these paths — no
 * forwarding controller, no history-API fallback.
 *
 * Recognised routes: `#/` (inbox) and `#/proposals/{id}` (detail).
 */
export function useHashRoute() {
  const [hash, setHash] = useState(() => window.location.hash)

  useEffect(() => {
    const onChange = () => setHash(window.location.hash)
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])

  const match = hash.match(/^#\/proposals\/(\d+)$/)
  return match ? { view: 'detail', id: Number(match[1]) } : { view: 'list' }
}

export const navigate = (path) => {
  window.location.hash = path
}
