import { useEffect, useState } from 'react'
import { listProfiles } from './api.js'
import Inbox from './Inbox.jsx'
import ProposalDetail from './ProposalDetail.jsx'
import { useHashRoute } from './useHashRoute.js'

export default function App() {
  const route = useHashRoute()
  const [profiles, setProfiles] = useState([])
  const [error, setError] = useState(null)
  // Held here rather than in the form: generating navigates to the detail view, and a warning
  // about a repository that could not be read has to survive that jump.
  const [warnings, setWarnings] = useState([])

  useEffect(() => {
    listProfiles()
      .then(setProfiles)
      .catch((e) => setError(e.message))
  }, [])

  return (
    <div className="app">
      <header className="app-header">
        <a href="#/" className="brand">
          mite-sync
        </a>
        <span className="tagline">proposal inbox</span>
      </header>

      {error && (
        <div className="banner banner-error" role="alert">
          <span>{error}</span>
          <button type="button" className="link" onClick={() => setError(null)}>
            dismiss
          </button>
        </div>
      )}

      {warnings.length > 0 && (
        <div className="banner banner-warning" role="status">
          <div>
            <strong>Generated with warnings</strong>
            <ul>
              {warnings.map((w, i) => (
                <li key={i}>{w}</li>
              ))}
            </ul>
          </div>
          <button type="button" className="link" onClick={() => setWarnings([])}>
            dismiss
          </button>
        </div>
      )}

      <main>
        {route.view === 'detail' ? (
          <ProposalDetail id={route.id} onError={setError} />
        ) : (
          <Inbox profiles={profiles} onError={setError} onWarnings={setWarnings} />
        )}
      </main>
    </div>
  )
}
