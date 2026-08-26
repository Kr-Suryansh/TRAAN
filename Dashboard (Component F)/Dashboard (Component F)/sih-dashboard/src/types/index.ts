// ============================================================
// sih-dashboard · src/types/index.ts
// Matches day1-contracts-and-repo-setup.md §1 schemas exactly.
// DO NOT rename fields — these must stay in sync with backend.
// ============================================================

// §1.1
export interface UserMedicalProfile {
  name: string | null;
  age: number | null;
  blood_type: string | null;
  medical_conditions: string[];
  medications: string[];
  allergies: string[];
  emergency_contact_name: string | null;
  emergency_contact_number: string | null;
}

// §1.2
export type EmergencyType =
  | 'medical'
  | 'trapped'
  | 'structural_collapse'
  | 'flood_rescue'
  | 'fire'
  | 'missing_person'
  | 'unspecified';

export type SeverityHint = 'critical' | 'high' | 'medium' | 'low';

export interface SOSRequest {
  uuid: string;
  device_id: string;
  created_at: string; // ISO8601
  location: {
    lat: number;
    lng: number;
    accuracy_m: number | null;
  };
  is_quick_sos: boolean;
  emergency_type: EmergencyType;
  severity_hint: SeverityHint | null;
  people_count: number | null;
  medical_snapshot: UserMedicalProfile | null;
  custom_message: string | null;
  contact_number: string | null;
  relay_hop_count: number;
  last_relayed_at: string; // ISO8601
  status: 'pending_local' | 'in_relay' | 'uploaded';
}

// §1.5 — primary dashboard data shape
export type IncidentSeverity = 'critical' | 'high' | 'medium' | 'low';
export type IncidentStatus = 'new' | 'acknowledged' | 'dispatched' | 'resolved';

export interface IncidentFlags {
  medical_emergency: boolean;
  trapped: boolean;
  elderly_or_children: boolean;
  structural_damage: boolean;
}

export interface RecommendedResource {
  /** Canonical Day 1 field — required. Must match resource category/sub_type. */
  resource_type: string;
  quantity: number;
  reasoning: string;
  /**
   * Optional: future compat adapter field only.
   * The canonical contract is resource_type; resource_id is NOT in the Day 1
   * recommended_resources spec. Include only if backend normalization requires it
   * and isolate it outside display logic.
   */
  resource_id?: string;
  agency?: string;
  distance_km?: number;
}

export interface Incident {
  incident_id: string;
  cluster_id: string;
  source_sos_uuids: string[];
  location: { lat: number; lng: number };
  area_name: string;
  emergency_types: string[];
  severity: IncidentSeverity;
  ai_summary: string | null;
  report_count: number;
  estimated_people_affected: number;
  flags: IncidentFlags;
  first_reported_at: string; // ISO8601
  last_updated_at: string; // ISO8601
  status: IncidentStatus;
  recommended_resources: RecommendedResource[];
  assigned_resources: string[];
}

// §1.6
export type ResourceCategory = 'medical' | 'rescue' | 'shelter' | 'transport' | 'communication';
export type ResourceStatus = 'available' | 'partially_deployed' | 'deployed' | 'maintenance';

export interface Resource {
  resource_id: string;
  category: ResourceCategory;
  sub_type: string;
  custodian_agency: string;
  quantity_total: number;
  quantity_available: number;
  status: ResourceStatus;
  location: { lat: number; lng: number; district: string };
  contact: string;
  last_updated_at: string; // ISO8601
}

// §1.7
export type DispatchStatus = 'dispatched' | 'en_route' | 'arrived' | 'completed';

export interface DispatchRecord {
  dispatch_id: string;
  incident_id: string;
  resource_id: string;
  quantity_dispatched: number;
  dispatched_by: string;
  dispatched_at: string; // ISO8601
  eta_minutes: number | null;
  status: DispatchStatus;
}

// §1.8
export type AuthorityRole = 'DDMA' | 'police' | 'fire' | 'ambulance' | 'NDRF' | 'SDRF' | 'admin';

export interface AuthorityUser {
  user_id: string;
  name: string;
  role: AuthorityRole;
  agency: string;
  email: string;
}

// §2 Misc — GET /stats/summary
export interface StatsSummary {
  active_incidents: {
    critical: number;
    high: number;
    medium: number;
    low: number;
  };
  total_estimated_people_affected: number;
  resources: {
    available: number;
    deployed: number;
  };
  new_incidents_last_15min: number;
}

// §3 WebSocket event shapes
export type WsEventName =
  | 'incident_created'
  | 'incident_updated'
  | 'incident_dispatched'
  | 'resource_updated'
  | 'situation_brief_updated';

export interface WsMessage {
  event: WsEventName;
  data: Incident | DispatchRecord | Resource | SituationBriefPayload;
}

export interface SituationBriefPayload {
  text: string;
  updated_at: string; // ISO8601
}

// Auth
export interface LoginResponse {
  access_token: string;
  refresh_token: string;
  user: AuthorityUser;
}

// Dispatch request body
export interface DispatchRequest {
  resource_id: string;
  quantity: number;
}
