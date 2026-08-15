"""
resource_registry.py
Helper module for fetching and filtering available resources from the resource store.
"""
from typing import List
from app.models.schemas import Resource, ResourceStatus
from app.services.mock_idrn.seed import seed_database

def get_all_resources() -> List[Resource]:
    """
    Fetch all resources from the mock IDRN registry.
    """
    return seed_database()

def get_available_resources(resources: List[Resource] = None) -> List[Resource]:
    """
    Filters resources to return only those eligible for optimization:
    - status == ResourceStatus.available
    - quantity_available > 0
    """
    if resources is None:
        resources = get_all_resources()
        
    return [
        r for r in resources
        if r.status == ResourceStatus.available and r.quantity_available > 0
    ]
