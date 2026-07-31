import { formatMinutes, isGenerated } from './format.js'

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
                    value={entry.minutes}
                    onChange={(e) => update(index, { minutes: Number(e.target.value) })}
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
