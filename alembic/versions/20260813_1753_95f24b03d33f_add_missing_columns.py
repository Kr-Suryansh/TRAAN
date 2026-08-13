"""Add missing columns

Revision ID: 95f24b03d33f
Revises: 
Create Date: 2026-08-13 17:53:09.520606+00:00
"""

from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa
import geoalchemy2


# revision identifiers, used by Alembic.
revision: str = '95f24b03d33f'
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column('authority', sa.Column('name', sa.String(), nullable=True))
    op.add_column('authority', sa.Column('agency', sa.String(), nullable=True))
    op.add_column('device', sa.Column('last_seen_at', sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    op.drop_column('device', 'last_seen_at')
    op.drop_column('authority', 'agency')
    op.drop_column('authority', 'name')
