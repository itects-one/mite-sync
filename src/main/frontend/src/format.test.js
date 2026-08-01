import { describe, expect, it } from 'vitest'
import { formatMinutes, isGenerated, todayIso } from './format.js'

describe('formatMinutes', () => {
  it('renders minutes as h:mm', () => {
    expect(formatMinutes(375)).toBe('6:15')
    expect(formatMinutes(60)).toBe('1:00')
    expect(formatMinutes(5)).toBe('0:05')
    expect(formatMinutes(0)).toBe('0:00')
  })

  it('treats missing values as zero', () => {
    expect(formatMinutes(undefined)).toBe('0:00')
    expect(formatMinutes(null)).toBe('0:00')
  })

  it('keeps the sign of a negative remainder', () => {
    expect(formatMinutes(-90)).toBe('-1:30')
  })
})

describe('todayIso', () => {
  it('returns a plain ISO date', () => {
    expect(todayIso()).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })
})

describe('isGenerated', () => {
  it('separates derived entries from hand-written ones', () => {
    expect(isGenerated('git')).toBe(true)
    expect(isGenerated('calendar')).toBe(true)
    expect(isGenerated('manual')).toBe(false)
    // Legacy rows written before provenance was derived server-side
    expect(isGenerated(null)).toBe(false)
  })
})
