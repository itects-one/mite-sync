import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from './api.js'
import ProposalDetail from './ProposalDetail.jsx'

vi.mock('./api.js')

const draft = (overrides = {}) => ({
  id: 1,
  date: '2026-07-20',
  profileKey: 'default',
  status: 'DRAFT',
  totalMinutes: 45,
  entries: [{ minutes: 45, note: '#VC-1 Fix', source: 'git' }],
  ...overrides,
})

const button = (name) => screen.getByRole('button', { name })

describe('ProposalDetail', () => {
  beforeEach(() => {
    api.getProposal.mockResolvedValue(draft())
  })

  it('offers confirming a saved draft', async () => {
    render(<ProposalDetail id={1} onError={() => {}} />)

    await screen.findByDisplayValue('#VC-1 Fix')
    expect(button('Confirm & book').disabled).toBe(false)
    // Nothing changed yet, so there is nothing to save
    expect(button('Save entries').disabled).toBe(true)
  })

  it('blocks confirming while there are unsaved changes', async () => {
    render(<ProposalDetail id={1} onError={() => {}} />)
    await screen.findByDisplayValue('#VC-1 Fix')

    await userEvent.type(screen.getAllByRole('textbox')[0], '!')

    // Confirming books what is stored, not what is on screen — so it has to wait for the save.
    expect(button('Confirm & book').disabled).toBe(true)
    expect(button('Save entries').disabled).toBe(false)
    expect(screen.getByText(/Unsaved changes/)).toBeDefined()
  })

  it('refuses to confirm an empty proposal', async () => {
    api.getProposal.mockResolvedValue(draft({ entries: [], totalMinutes: 0 }))
    render(<ProposalDetail id={1} onError={() => {}} />)
    await screen.findByText('No entries.')

    // An empty proposal would report BOOKED without booking anything.
    expect(button('Confirm & book').disabled).toBe(true)
    expect(button('Save entries').disabled).toBe(true)
    expect(screen.getByText(/cannot be saved without entries/)).toBeDefined()
  })

  it('is read-only once the proposal is booked', async () => {
    api.getProposal.mockResolvedValue(draft({ status: 'BOOKED' }))
    render(<ProposalDetail id={1} onError={() => {}} />)
    // Read-only: the note is plain text, not an input
    await screen.findByText('#VC-1 Fix')

    expect(screen.queryByRole('button', { name: 'Confirm & book' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Save entries' })).toBeNull()
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.getByText(/Only a DRAFT can be edited/)).toBeDefined()
  })

  it('saves the edited entries', async () => {
    api.saveEntries.mockResolvedValue(draft({ entries: [{ minutes: 60, note: 'x', source: 'manual' }] }))
    render(<ProposalDetail id={1} onError={() => {}} />)
    await screen.findByDisplayValue('#VC-1 Fix')

    await userEvent.type(screen.getAllByRole('textbox')[0], '!')
    await userEvent.click(button('Save entries'))

    expect(api.saveEntries).toHaveBeenCalledWith(1, [
      { minutes: 45, note: '#VC-1 Fix!', source: 'git' },
    ])
  })

  it('reports the booking outcome after confirming', async () => {
    api.confirmProposal.mockResolvedValue({
      proposal: draft({ status: 'PARTIALLY_BOOKED' }),
      booking: {
        created: [{ minutes: 45 }],
        failed: [{ minutes: 15, note: '#595 Daily', error: 'boom' }],
      },
    })
    render(<ProposalDetail id={1} onError={() => {}} />)
    await screen.findByDisplayValue('#VC-1 Fix')

    await userEvent.click(button('Confirm & book'))

    await screen.findByText('Booking result')
    expect(screen.getByText('1 entry created, 1 failed.')).toBeDefined()
    expect(screen.getByText(/boom/)).toBeDefined()
  })

  it('asks again before deleting', async () => {
    api.deleteProposal.mockResolvedValue(null)
    render(<ProposalDetail id={1} onError={() => {}} />)
    await screen.findByDisplayValue('#VC-1 Fix')

    await userEvent.click(button('Delete'))
    expect(api.deleteProposal).not.toHaveBeenCalled()

    await userEvent.click(button('Really delete'))
    expect(api.deleteProposal).toHaveBeenCalledWith(1)
  })

  it('surfaces a failed load', async () => {
    api.getProposal.mockRejectedValue(new Error('proposal 9 not found'))
    const onError = vi.fn()

    render(<ProposalDetail id={9} onError={onError} />)

    await waitFor(() => expect(onError).toHaveBeenCalledWith('proposal 9 not found'))
  })
})
