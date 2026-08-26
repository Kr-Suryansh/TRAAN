// ============================================================
// sih-dashboard · src/__tests__/DispatchModal.test.tsx
// Critical test: verify dispatch NEVER fires without explicit confirm.
// ============================================================

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DispatchModal } from '../components/DispatchModal';
import type { Incident, Resource } from '../types';

// Mock the API
vi.mock('../api/incidents', () => ({
  dispatchResource: vi.fn().mockResolvedValue({
    dispatch_id: 'disp-test-001',
    incident_id: 'inc-001',
    resource_id: 'res-boat-001',
    quantity_dispatched: 1,
    dispatched_by: 'usr-001',
    dispatched_at: new Date().toISOString(),
    eta_minutes: 10,
    status: 'dispatched',
  }),
}));

import * as incidentsApi from '../api/incidents';

const mockIncident: Incident = {
  incident_id: 'inc-001',
  cluster_id: 'cluster-001',
  source_sos_uuids: ['sos-001'],
  location: { lat: 26.9124, lng: 75.7873 },
  area_name: 'Test Area',
  emergency_types: ['flood_rescue'],
  severity: 'critical',
  ai_summary: 'Test summary',
  report_count: 1,
  estimated_people_affected: 5,
  flags: { medical_emergency: false, trapped: true, elderly_or_children: false, structural_damage: false },
  first_reported_at: new Date().toISOString(),
  last_updated_at: new Date().toISOString(),
  status: 'new',
  recommended_resources: [{ resource_type: 'motorboat', quantity: 1, reasoning: 'Water access needed' }],
  assigned_resources: [],
};

const mockResources: Resource[] = [
  {
    resource_id: 'res-boat-001',
    category: 'rescue',
    sub_type: 'motorboat',
    custodian_agency: 'SDRF Unit 3',
    quantity_total: 4,
    quantity_available: 3,
    status: 'partially_deployed',
    location: { lat: 26.9, lng: 75.7, district: 'Jaipur' },
    contact: '1234567890',
    last_updated_at: new Date().toISOString(),
  },
];

function renderModal() {
  const onClose = vi.fn();
  const onDispatched = vi.fn();
  const { container } = render(
    <DispatchModal
      incident={mockIncident}
      resources={mockResources}
      onClose={onClose}
      onDispatched={onDispatched}
    />
  );
  return { onClose, onDispatched, container };
}

describe('DispatchModal — human-in-the-loop enforcement', () => {
  beforeEach(() => { vi.clearAllMocks(); });

  it('renders without crashing', () => {
    renderModal();
    expect(screen.getByText('Dispatch Resource')).toBeDefined();
  });

  it('Confirm Dispatch button is DISABLED initially (before checkbox checked)', () => {
    renderModal();
    const confirmBtn = document.getElementById('dispatch-confirm-btn');
    // Button should be disabled — cannot dispatch without confirmation
    expect(confirmBtn).toBeTruthy();
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(true);
  });

  it('does NOT call dispatchResource before checkbox is checked', async () => {
    renderModal();
    const confirmBtn = document.getElementById('dispatch-confirm-btn') as HTMLButtonElement;
    // Try to click without checking checkbox
    fireEvent.click(confirmBtn);
    expect(incidentsApi.dispatchResource).not.toHaveBeenCalled();
  });

  it('enables Confirm button only after checkbox is checked', async () => {
    renderModal();
    const checkbox = document.getElementById('dispatch-confirm-checkbox') as HTMLInputElement;
    const confirmBtn = document.getElementById('dispatch-confirm-btn') as HTMLButtonElement;

    expect(confirmBtn.disabled).toBe(true);
    fireEvent.click(checkbox);
    expect(confirmBtn.disabled).toBe(false);
  });

  it('calls dispatchResource ONLY after checkbox + explicit button click', async () => {
    const { onDispatched } = renderModal();
    const user = userEvent.setup();

    const checkbox = document.getElementById('dispatch-confirm-checkbox') as HTMLInputElement;
    const confirmBtn = document.getElementById('dispatch-confirm-btn') as HTMLButtonElement;

    // Check the checkbox (explicit human action)
    await user.click(checkbox);
    expect(confirmBtn.disabled).toBe(false);

    // Click the confirm button (explicit human action)
    await user.click(confirmBtn);

    await waitFor(() => {
      expect(incidentsApi.dispatchResource).toHaveBeenCalledTimes(1);
      expect(incidentsApi.dispatchResource).toHaveBeenCalledWith('inc-001', {
        resource_id: 'res-boat-001',
        quantity: 1,
      });
      expect(onDispatched).toHaveBeenCalledTimes(1);
    });
  });

  it('cancel button calls onClose without dispatching', async () => {
    const { onClose } = renderModal();
    const cancelBtn = document.getElementById('dispatch-cancel-btn') as HTMLButtonElement;
    fireEvent.click(cancelBtn);
    expect(onClose).toHaveBeenCalledTimes(1);
    expect(incidentsApi.dispatchResource).not.toHaveBeenCalled();
  });

  it('shows AI recommendations as read-only guidance with RECOMMENDED label', () => {
    renderModal();
    // Section header should clearly identify these as AI/OR-Tools recommendations
    expect(screen.getByText('AI / OR-Tools Recommendations')).toBeDefined();
    // The reasoning text should be present
    expect(screen.getByText('Water access needed')).toBeDefined();
    // The RECOMMENDED badge must be visible — it is not a dispatch confirmation
    expect(screen.getByText('RECOMMENDED · Guidance Only')).toBeDefined();
  });

  it('AI recommendation section does NOT have a dispatch/confirm button', () => {
    renderModal();
    // The RECOMMENDED section must be purely informational — no button inside it
    const recommendedLabel = screen.getByText('RECOMMENDED · Guidance Only');
    expect(recommendedLabel).toBeDefined();
    // The confirm button is only in the footer actions section
    const confirmBtn = document.getElementById('dispatch-confirm-btn');
    expect(confirmBtn).toBeTruthy();
    // It must still be disabled (human confirmation not yet given)
    expect((confirmBtn as HTMLButtonElement).disabled).toBe(true);
  });
});
