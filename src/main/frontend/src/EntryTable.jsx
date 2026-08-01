import { MAX_MINUTES, formatMinutes, isGenerated } from './format.js'

const clamp = (minutes) => Math.min(MAX_MINUTES, Math.max(0, Number(minutes) || 0))

/**
 * The entries of a proposal. Editable only while DRAFT; afterwards the booked truth is shown
 * read-only.
 *
 * The `source` column is what distinguishes an entry the app derived from evidence from one a
 * human typed. It is derived server-side and cannot be edited here — changing an entry is what
 * turns it into a manual one.
 */
export default function EntryTable({ entries, editable, onChange }) {
  const update = (index, patch) =>
    onChange(entries.map((entry, i) => (i === index ? { ...entry, ...patch } : entry)))

  const remove = (index) => onChange(entries.filter((_, i) => i !== index))

  const add = () => onChange([...entries, { minutes: 0, note: '', source: 'manual' }])

  const total = entries.reduce((sum, e) => sum + (Number(e.minutes) || 0), 0)

  return (
    <>
      <table className="table">
        <thead>
          <tr>
            <th className="minutes">Minutes</th>
            <th>Note</th>
            <th>Source</th>
            {editable && <th />}
          </tr>
        </thead>
        <tbody>
          {entries.length === 0 && (
            <tr>
              <td colSpan={editable ? 4 : 3} className="muted">
                No entries.
              </td>
            </tr>
          )}
          {entries.map((entry, index) => (
            <tr key={index}>
              <td className="minutes">
                {editable ? (
                  <input
                    type="number"
                    min="0"
                    max={MAX_MINUTES}
                    value={entry.minutes}
                    // min/max are only form-validation hints and this form is never submitted, so
                    // the clamp has to happen here. Both ends matter: a negative value would be
                    // booked as such, and one past MAX_MINUTES is rejected by the server anyway.
                    onChange={(e) => update(index, { minutes: clamp(Number(e.target.value)) })}
                  />
                ) : (
                  <span className="mono">{entry.minutes}</span>
                )}
              </td>
              <td>
                {editable ? (
                  <input
                    type="text"
                    value={entry.note ?? ''}
                    onChange={(e) => update(index, { note: e.target.value })}
                  />
                ) : (
                  entry.note
                )}
              </td>
              <td>
                <span className={`badge ${isGenerated(entry.source) ? 'generated' : 'manual'}`}>
                  {entry.source ?? 'unknown'}
                </span>
              </td>
              {editable && (
                <td className="right">
                  <button type="button" className="link" onClick={() => remove(index)}>
                    remove
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td className="minutes mono">{total}</td>
            <td className="muted">{formatMinutes(total)} in total</td>
            <td colSpan={editable ? 2 : 1} />
          </tr>
        </tfoot>
      </table>

      {editable && (
        <button type="button" onClick={add}>
          Add entry
        </button>
      )}
    </>
  )
}
