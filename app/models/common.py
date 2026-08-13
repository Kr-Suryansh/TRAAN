from enum import Enum


class AuthorityRole(str, Enum):
    DDMA = "DDMA"
    police = "police"
    fire = "fire"
    ambulance = "ambulance"
    NDRF = "NDRF"
    SDRF = "SDRF"
    admin = "admin"


class EmergencyType(str, Enum):
    medical = "medical"
    trapped = "trapped"
    structural_collapse = "structural_collapse"
    flood_rescue = "flood_rescue"
    fire = "fire"
    missing_person = "missing_person"
    unspecified = "unspecified"


class SeverityHint(str, Enum):
    critical = "critical"
    high = "high"
    medium = "medium"
    low = "low"


class SOSStatus(str, Enum):
    pending_local = "pending_local"
    in_relay = "in_relay"
    uploaded = "uploaded"
