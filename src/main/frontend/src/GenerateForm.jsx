import { useEffect, useState } from 'react'
import { generateProposal } from './api.js'
import { todayIso } from './format.js'
import { navigate } from './useHashRoute.js'

/**
 * Generates (or regenerates) the DRAFT for a profile and date. Regenerating overwrites an existing
 * DRAFT for the same day in place, so repeating this is safe and never produces duplicates.
 */
export default function GenerateForm({ profiles, onError, onWarnings, onGenerated }) {
  const [profileKey, setProfileKey] = useState('')
  const [date, setDate] = useState(todayIso)
  const [mainPbiId, setMainPbiId] = useState('')
  const [targetHours, setTargetHours] = useState('')
  const [busy, setBusy] = useState(false)

  // Preselect the profile the legacy routes fall back to, once the list has arrived.
  useEffect(() => {
    if (profileKey === '' && profiles.length > 0) {
      setProfileKey((profiles.find((p) => p.default) ?? profiles[0]).key)
    }
  }, [profiles, profileKey])

  const profile = profiles.find((p) => p.key === profileKey)
  const needsMainPbi = profile?.requiresMainPbi ?? false
  const canSubmit = profileKey !== '' && date !== '' && (!needsMainPbi || mainPbiId !== '') && !busy

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    try {
      // Omit what the user left empty — the server falls back to the profile's own defaults.
      // The main PBI only goes out where the profile asks for one: otherwise its input is hidden
      // and we would send a value the user cannot see.
      const assignment = {}
      if (needsMainPbi && mainPbiId !== '') assignment.mainPbiId = Number(mainPbiId)
      if (targetHours !== '') assignment.targetHours = Number(targetHours)

      const proposal = await generateProposal(profileKey, date, assignment)
      // An empty proposal and a repository that could not be read look identical otherwise.
      onWarnings(proposal.warnings ?? [])
      await onGenerated()
      navigate(`/proposals/${proposal.id}`)
    } catch (e) {
      onError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="card">
      <h2>Generate a proposal</h2>
      <form className="form-row" onSubmit={submit}>
        <label>
          Profile
          <select value={profileKey} onChange={(e) => setProfileKey(e.target.value)}>
            {profiles.length === 0 && <option value="">no profiles configured</option>}
            {profiles.map((p) => (
              <option key={p.key} value={p.key}>
                {p.key} ({p.workflowType})
              </option>
            ))}
          </select>
        </label>

        <label>
          Date
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>

        {needsMainPbi && (
          <label>
            Main PBI
            <input
              type="number"
              value={mainPbiId}
              onChange={(e) => setMainPbiId(e.target.value)}
              placeholder="required"
            />
          </label>
        )}

        <label>
          Target hours
          <input
            type="number"
            step="0.25"
            min="0"
            value={targetHours}
            onChange={(e) => setTargetHours(e.target.value)}
            placeholder={profile ? (profile.targetMinutes / 60).toFixed(2) : ''}
          />
        </label>

        <button type="submit" className="primary" disabled={!canSubmit}>
          {busy ? 'Generating…' : 'Generate'}
        </button>
      </form>
    </section>
  )
}
