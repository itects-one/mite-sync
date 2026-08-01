import { useCallback, useEffect, useState } from 'react'
import { listProposals } from './api.js'
import GenerateForm from './GenerateForm.jsx'
import StatusBadge from './StatusBadge.jsx'
import { formatMinutes } from './format.js'

export default function Inbox({ profiles, onError, onWarnings }) {
  const [proposals, setProposals] = useState(null)
  const [loadFailed, setLoadFailed] = useState(false)

  const reload = useCallback(
    () =>
      listProposals()
        .then((p) => {
          setProposals(p)
          setLoadFailed(false)
        })
        .catch((e) => {
          // Otherwise the list would sit on "Loading…" while the banner reports the failure.
          setLoadFailed(true)
          onError(e.message)
        }),
    [onError],
  )

  useEffect(() => {
    reload()
  }, [reload])

  return (
    <>
      <GenerateForm
        profiles={profiles}
        onError={onError}
        onWarnings={onWarnings}
        onGenerated={reload}
      />

      <section className="card">
        <h2>Inbox</h2>
        {proposals === null && !loadFailed && <p className="muted">Loading…</p>}
        {proposals === null && loadFailed && (
          <p>
            Could not load the inbox.{' '}
            <button type="button" className="link" onClick={reload}>
              Try again
            </button>
          </p>
        )}
        {proposals !== null && proposals.length === 0 && (
          <p className="muted">No proposals yet — generate one above.</p>
        )}
        {proposals !== null && proposals.length > 0 && (
          <table className="table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Profile</th>
                <th>Status</th>
                <th className="right">Total</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {proposals.map((p) => (
                <tr key={p.id}>
                  <td>{p.date}</td>
                  <td>{p.profileKey}</td>
                  <td>
                    <StatusBadge status={p.status} />
                  </td>
                  <td className="right mono">{formatMinutes(p.totalMinutes)}</td>
                  <td className="right">
                    <a href={`#/proposals/${p.id}`}>
                      {p.status === 'DRAFT' ? 'review' : 'view'}
                    </a>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </>
  )
}
