#!/usr/bin/env bash
# Read-only cost check: current Cloud SQL sizing, Google's right-sizing recommendations, and
# the uploads bucket's size and lifecycle. Changes nothing.
#   PROJECT=docket-java ./scripts/cost-report.sh
set -euo pipefail

PROJECT="${PROJECT:?set PROJECT}"
INSTANCE="${INSTANCE:-docket-db}"
REGION="${REGION:-europe-west3}"
BUCKET="${BUCKET:-docket-java-uploads}"

echo "== Cloud SQL: $INSTANCE"
gcloud sql instances describe "$INSTANCE" --project "$PROJECT" \
  --format='table(settings.tier,settings.availabilityType,settings.dataDiskSizeGb,settings.dataDiskType,settings.storageAutoResize,settings.edition)'

echo
echo "== Recommender: over-provisioned or idle instances"
for recommender in google.cloudsql.instance.OverprovisionedRecommender google.cloudsql.instance.IdleRecommender; do
  gcloud recommender recommendations list --project "$PROJECT" --location "$REGION" \
    --recommender "$recommender" --format='table(description,primaryImpact.costProjection.cost.units:label=MONTHLY_DELTA)' \
    2>/dev/null || echo "($recommender: no data or API disabled)"
done

echo
echo "== GCS: gs://$BUCKET"
gcloud storage du -s "gs://$BUCKET" --readable-sizes 2>/dev/null || echo "(no access)"
gcloud storage buckets describe "gs://$BUCKET" --format='yaml(lifecycle_config,default_storage_class,location)'
