import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from './api.js'
import App from './App.jsx'

vi.mock('./api.js')

const profiles = [
  { key: 'default', workflowType: 'calendar-devops', requiresMainPbi: true, targetMinutes: 375, default: true },
  { key: 'side', workflowType: 'git-activity', requiresMainPbi: false, targetMinutes: 240, default: false },
]

describe('App', () => {
  beforeEach(() => {
    window.location.hash = ''
    api.listProfiles.mockResolvedValue(profiles)
    api.listProposals.mockResolvedValue([])
    // Generating navigates to the detail view, which loads the new proposal right away
    api.getProposal.mockResolvedValue({
      id: 7,
      date: '2026-07-20',
      profileKey: 'side',
      status: 'DRAFT',
      totalMinutes: 0,
      entries: [],
    })
  })

  it('shows an empty inbox', async () => {
    render(<App />)
    expect(await screen.findByText(/No proposals yet/)).toBeDefined()
  })

  it('lists stored proposals with their status and total', async () => {
    api.listProposals.mockResolvedValue([
      { id: 3, date: '2026-07-20', profileKey: 'default', status: 'BOOKED', totalMinutes: 375 },
    ])

    render(<App />)

    expect(await screen.findByText('2026-07-20')).toBeDefined()
    expect(screen.getByText('BOOKED')).toBeDefined()
    expect(screen.getByText('6:15')).toBeDefined()
  })

  it('asks for a main PBI only where the profile needs one', async () => {
    render(<App />)

    // Wait for the form itself: it fills from listProfiles, which is a different promise than the
    // proposal list — awaiting the list would leave the preselection racing.
    expect(await screen.findByLabelText(/Main PBI/)).toBeDefined()

    await userEvent.selectOptions(screen.getByLabelText(/Profile/), 'side')

    expect(screen.queryByLabelText(/Main PBI/)).toBeNull()
  })

  it('omits what the user left empty when generating', async () => {
    api.generateProposal.mockResolvedValue({ id: 7 })
    render(<App />)

    // The option itself has to be there — the select renders before the profiles arrive.
    await screen.findByRole('option', { name: /side/ })
    await userEvent.selectOptions(screen.getByLabelText(/Profile/), 'side')
    await userEvent.click(screen.getByRole('button', { name: 'Generate' }))

    expect(api.generateProposal).toHaveBeenCalledWith('side', expect.any(String), {})
  })

  it('does not send a main PBI the user cannot see', async () => {
    api.generateProposal.mockResolvedValue({ id: 7 })
    render(<App />)

    // Typed for a calendar-devops profile, then switched to one that hides the field.
    await userEvent.type(await screen.findByLabelText(/Main PBI/), '12345')
    await userEvent.selectOptions(screen.getByLabelText(/Profile/), 'side')
    await userEvent.click(screen.getByRole('button', { name: 'Generate' }))

    expect(api.generateProposal).toHaveBeenCalledWith('side', expect.any(String), {})
  })

  it('does not leave the inbox loading when the list fails', async () => {
    api.listProposals.mockRejectedValue(new Error('Not authenticated'))

    render(<App />)

    expect(await screen.findByText(/Could not load the inbox/)).toBeDefined()
    expect(screen.queryByText('Loading…')).toBeNull()

    api.listProposals.mockResolvedValue([])
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByText(/No proposals yet/)).toBeDefined()
  })

  it('surfaces an API error in a banner', async () => {
    api.listProfiles.mockRejectedValue(new Error('Not authenticated'))

    render(<App />)

    const banner = await screen.findByRole('alert')
    expect(banner.textContent).toContain('Not authenticated')
  })
})
