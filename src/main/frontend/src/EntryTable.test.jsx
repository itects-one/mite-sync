import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import EntryTable from './EntryTable.jsx'

const entries = [
  { minutes: 45, note: '#VC-1 Fix', source: 'git' },
  { minutes: 15, note: '#595 Daily', source: 'calendar' },
]

describe('EntryTable', () => {
  it('sums the minutes of all entries', () => {
    render(<EntryTable entries={entries} editable={false} onChange={() => {}} />)
    expect(screen.getByText('1:00 in total')).toBeDefined()
  })

  it('shows provenance so a derived entry is distinguishable from a typed one', () => {
    render(
      <EntryTable
        entries={[...entries, { minutes: 30, note: 'typed', source: 'manual' }]}
        editable={false}
        onChange={() => {}}
      />,
    )
    expect(screen.getByText('git').className).toContain('generated')
    expect(screen.getByText('manual').className).toContain('manual')
  })

  it('renders read-only without inputs', () => {
    render(<EntryTable entries={entries} editable={false} onChange={() => {}} />)
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.queryByRole('button', { name: 'Add entry' })).toBeNull()
  })

  it('reports an edited note', async () => {
    const onChange = vi.fn()
    render(<EntryTable entries={entries} editable onChange={onChange} />)

    await userEvent.type(screen.getAllByRole('textbox')[0], '!')

    expect(onChange).toHaveBeenCalledWith([
      { minutes: 45, note: '#VC-1 Fix!', source: 'git' },
      entries[1],
    ])
  })

  it('adds a row marked as manual', async () => {
    const onChange = vi.fn()
    render(<EntryTable entries={entries} editable onChange={onChange} />)

    await userEvent.click(screen.getByRole('button', { name: 'Add entry' }))

    expect(onChange).toHaveBeenCalledWith([...entries, { minutes: 0, note: '', source: 'manual' }])
  })

  it('removes the row that was clicked', async () => {
    const onChange = vi.fn()
    render(<EntryTable entries={entries} editable onChange={onChange} />)

    await userEvent.click(screen.getAllByRole('button', { name: 'remove' })[0])

    expect(onChange).toHaveBeenCalledWith([entries[1]])
  })
})
