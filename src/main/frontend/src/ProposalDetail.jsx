import { useCallback, useEffect, useState } from 'react'
import { confirmProposal, deleteProposal, getProposal, saveEntries } from './api.js'
import EntryTable from './EntryTable.jsx'
import StatusBadge from './StatusBadge.jsx'
import { formatMinutes } from './format.js'
import { navigate } from './useHashRoute.js'

export default function ProposalDetail({ id, onError }) {
  const [proposal, setProposal] = useState(null)
  const [entries, setEntries] = useState([])
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const [armedForDelete, setArmedForDelete] = useState(false)
  const [loadFailed, setLoadFailed] = useState(false)

  const load = useCallback(
    () =>
      getProposal(id)
        .then((p) => {
          setProposal(p)
          setEntries(p.entries)
          setLoadFailed(false)
        })
        .catch((e) => {
          // Without this the view would claim to be loading forever — a stale bookmark or a
          // proposal deleted in another tab both end up here.
          setLoadFailed(true)
          onError(e.message)
        }),
    [id, onError],
  )

  useEffect(() => {
    load()
  }, [load])

  const breadcrumb = (
    <p className="breadcrumb">
      <a href="#/">← Inbox</a>
    </p>
  )

  if (proposal === null) {
    return (
      <>
        {breadcrumb}
        {loadFailed ? (
          <section className="card">
            <p>Could not load proposal {id}. See the message above.</p>
            <button type="button" onClick={load}>
              Try again
            </button>
          </section>
        ) : (
          <p className="muted">Loading…</p>
        )}
      </>
    )
  }

  const isDraft = proposal.status === 'DRAFT'
  const dirty = JSON.stringify(entries) !== JSON.stringify(proposal.entries)
  // An entry of zero minutes books nothing; a cleared input lands here too.
  const hasEmptyEntry = entries.some((e) => !(Number(e.minutes) > 0))
  const savable = dirty && entries.length > 0 && !hasEmptyEntry

  async function run(action) {
    setBusy(true)
    try {
      return await action()
    } catch (e) {
      onError(e.message)
      return null
    } finally {
      setBusy(false)
    }
  }

  const save = () =>
    run(async () => {
      const updated = await saveEntries(id, entries)
      setProposal(updated)
      setEntries(updated.entries)
    })

  const confirm = () =>
    run(async () => {
      const outcome = await confirmProposal(id)
      setProposal(outcome.proposal)
      setEntries(outcome.proposal.entries)
      setResult(outcome.booking)
    })

  const remove = () =>
    run(async () => {
      await deleteProposal(id)
      navigate('/')
    })

  return (
    <>
      {breadcrumb}

      <section className="card">
        <div className="detail-head">
          <h2>
            {proposal.date} <span className="muted">· {proposal.profileKey}</span>
          </h2>
          <StatusBadge status={proposal.status} />
        </div>
        <p className="muted">
          {formatMinutes(proposal.totalMinutes)} stored
          {proposal.bookedAt && ` · booked ${new Date(proposal.bookedAt).toLocaleString()}`}
        </p>

        <EntryTable entries={entries} editable={isDraft && !busy} onChange={setEntries} />

        {isDraft && entries.length === 0 && (
          <p className="hint">
            A proposal cannot be saved without entries. Discard your changes to get the stored ones
            back, or delete the proposal to get rid of it entirely.
          </p>
        )}
        {isDraft && hasEmptyEntry && entries.length > 0 && (
          <p className="hint">Every entry needs more than zero minutes.</p>
        )}
        {isDraft && dirty && savable && (
          <p className="hint">Unsaved changes — save them before confirming.</p>
        )}
        {!isDraft && (
          <p className="hint">
            Only a DRAFT can be edited or confirmed. This one is {proposal.status}.
          </p>
        )}

        <div className="actions">
          {isDraft && (
            <>
              <button type="button" onClick={save} disabled={busy || !savable}>
                Save entries
              </button>
              {dirty && (
                <button
                  type="button"
                  onClick={() => setEntries(proposal.entries)}
                  disabled={busy}
                  title="Back to the stored entries"
                >
                  Discard changes
                </button>
              )}
              <button
                type="button"
                className="primary"
                onClick={confirm}
                disabled={busy || dirty || entries.length === 0}
                title={
                  entries.length === 0
                    ? 'Nothing to book'
                    : dirty
                      ? 'Save your changes first — confirming books what is stored'
                      : undefined
                }
              >
                Confirm &amp; book
              </button>
            </>
          )}
          {armedForDelete ? (
            <>
              <button type="button" className="danger" onClick={remove} disabled={busy}>
                Really delete
              </button>
              <button type="button" onClick={() => setArmedForDelete(false)} disabled={busy}>
                Cancel
              </button>
            </>
          ) : (
            <button type="button" onClick={() => setArmedForDelete(true)} disabled={busy}>
              Delete
            </button>
          )}
        </div>
      </section>

      {result && (
        <section className="card">
          <h2>Booking result</h2>
          <p>
            {result.created.length} entr{result.created.length === 1 ? 'y' : 'ies'} created
            {result.failed.length > 0 && `, ${result.failed.length} failed`}.
          </p>
          {result.failed.length > 0 && (
            <ul className="failures">
              {result.failed.map((f, i) => (
                <li key={i}>
                  <span className="mono">{f.minutes}</span> {f.note} — {f.error}
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
    </>
  )
}
