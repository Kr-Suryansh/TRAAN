// ============================================================
// sih-dashboard · src/components/IncidentMap.tsx
// Leaflet map: severity-colour-coded incident markers + resource markers.
// Clicks incident marker → opens incident detail panel.
// ============================================================

import { useEffect } from 'react';
import {
  MapContainer,
  TileLayer,
  CircleMarker,
  Tooltip,
  Popup,
  Marker,
  useMap,
} from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import type { Incident, Resource } from '../types';

// Fix Leaflet default icon broken in Vite
delete (L.Icon.Default.prototype as unknown as Record<string, unknown>)._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon-2x.png',
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/images/marker-shadow.png',
});

const SEVERITY_COLORS: Record<string, string> = {
  critical: '#e53935',
  high:     '#f57c00',
  medium:   '#f9a825',
  low:      '#2e7d52',
};

const RESOURCE_COLOR = '#1565c0';

interface IncidentMapProps {
  incidents: Incident[];
  resources: Resource[];
  selectedIncidentId: string | null;
  onSelectIncident: (id: string) => void;
}

// Auto-fit bounds when incidents change and handle resize
function FitBounds({ incidents }: { incidents: Incident[] }) {
  const map = useMap();
  useEffect(() => {
    // Force map to recalculate size on mount/update to fix the white block issue
    const timer = setTimeout(() => {
      map.invalidateSize();
      if (incidents.length > 0) {
        const bounds = L.latLngBounds(
          incidents.map((i) => [i.location.lat, i.location.lng])
        );
        map.fitBounds(bounds, { padding: [40, 40], maxZoom: 14 });
      }
    }, 250);
    return () => clearTimeout(timer);
  }, [incidents, map]);
  return null;
}

export function IncidentMap({ incidents, resources, selectedIncidentId, onSelectIncident }: IncidentMapProps) {
  const center: [number, number] =
    incidents.length > 0
      ? [incidents[0].location.lat, incidents[0].location.lng]
      : [26.9124, 75.7873]; // Jaipur default

  return (
    <div style={{ width: '100%', height: '100%', borderRadius: 'var(--radius-lg)', overflow: 'hidden' }}>
      <MapContainer
        center={center}
        zoom={12}
        style={{ width: '100%', height: '100%' }}
        zoomControl={true}
      >
        <TileLayer
          url="https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
          attribution='&copy; <a href="https://carto.com/">CARTO</a>'
        />

        <FitBounds incidents={incidents} />

        {/* Incident markers */}
        {incidents.map((inc) => {
          const color = SEVERITY_COLORS[inc.severity] ?? '#888';
          const isSelected = inc.incident_id === selectedIncidentId;

          return (
            <CircleMarker
              key={inc.incident_id}
              center={[inc.location.lat, inc.location.lng]}
              radius={isSelected ? 18 : 12 + Math.min(inc.report_count * 2, 12)}
              pathOptions={{
                color,
                fillColor: color,
                fillOpacity: isSelected ? 0.95 : 0.75,
                weight: isSelected ? 3 : 2,
              }}
              eventHandlers={{ click: () => onSelectIncident(inc.incident_id) }}
            >
              <Tooltip
                permanent={false}
                direction="top"
                offset={[0, -10]}
              >
                <div style={{ fontSize: 12, lineHeight: 1.4, maxWidth: 200 }}>
                  <strong>{inc.area_name}</strong><br />
                  <span style={{ textTransform: 'capitalize' }}>{inc.severity}</span> · {inc.report_count} reports<br />
                  {inc.estimated_people_affected} affected
                </div>
              </Tooltip>
              <Popup>
                <div style={{ fontSize: 12 }}>
                  <strong>{inc.area_name}</strong><br />
                  {inc.ai_summary ?? 'Analysis pending…'}<br />
                  <button
                    style={{ marginTop: 6, background: '#1976d2', color: '#fff', border: 'none', borderRadius: 4, padding: '3px 10px', cursor: 'pointer' }}
                    onClick={() => onSelectIncident(inc.incident_id)}
                  >
                    View detail
                  </button>
                </div>
              </Popup>
            </CircleMarker>
          );
        })}

        {/* Resource markers */}
        {resources.map((res) => {
          const icon = L.divIcon({
            className: '',
            html: `<div style="
              width:10px;height:10px;border-radius:2px;
              background:${RESOURCE_COLOR};
              border:2px solid #fff;
              opacity:${res.status === 'deployed' ? 0.45 : 0.9};
            "></div>`,
            iconSize: [10, 10],
            iconAnchor: [5, 5],
          });
          return (
            <Marker
              key={res.resource_id}
              position={[res.location.lat, res.location.lng]}
              icon={icon}
            >
              <Tooltip direction="top" offset={[0, -8]}>
                <div style={{ fontSize: 12 }}>
                  <strong>{res.sub_type}</strong><br />
                  {res.custodian_agency}<br />
                  {res.quantity_available}/{res.quantity_total} available
                </div>
              </Tooltip>
            </Marker>
          );
        })}
      </MapContainer>
    </div>
  );
}
