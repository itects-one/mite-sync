/** Minutes as h:mm — the way a working day is read, not as a raw minute count. */
export function formatMinutes(minutes) {
  const total = Number(minutes) || 0
  const sign = total < 0 ? '-' : ''
  const abs = Math.abs(total)
  return `${sign}${Math.floor(abs / 60)}:${String(abs % 60).padStart(2, '0')}`
}

/** Today in the ISO form the date path variables expect, in local time. */
export function todayIso() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

/** Entries the app derived from evidence, as opposed to what a human typed. */
export const isGenerated = (source) => source != null && source !== 'manual'
