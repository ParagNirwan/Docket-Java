#!/usr/bin/env bash
# Creates a cross-region read replica of the production database for disaster recovery.
# europe-west4 (Netherlands) keeps the data inside the EU. Review before running.
#   PROJECT=docket-java ./scripts/dr-create-replica.sh
set -euo pipefail

PROJECT="${PROJECT:?set PROJECT}"
PRIMARY="${PRIMARY:-docket-db}"
REPLICA="${REPLICA:-docket-db-dr-euw4}"
DR_REGION="${DR_REGION:-europe-west4}"

# Same tier as the primary, so a promoted replica can take the full load.
TIER="$(gcloud sql instances describe "$PRIMARY" --project "$PROJECT" --format='value(settings.tier)')"
echo "Primary $PRIMARY tier: $TIER"

gcloud sql instances create "$REPLICA" \
  --project "$PROJECT" \
  --master-instance-name "$PRIMARY" \
  --region "$DR_REGION" \
  --tier "$TIER" \
  --availability-type ZONAL \
  --deletion-protection

echo "Replica $REPLICA created in $DR_REGION. Monitor lag with:"
echo "  gcloud sql instances describe $REPLICA --project $PROJECT --format='value(replicaConfiguration)'"
echo "Alert on cloudsql.googleapis.com/database/replication/replica_lag > 60s."
