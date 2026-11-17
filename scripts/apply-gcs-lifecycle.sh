#!/usr/bin/env bash
# Apply GCS lifecycle configuration to the production uploads bucket
# Run after verifying the lifecycle.json is correct

set -euo pipefail

BUCKET="docket-java-uploads"
LIFECYCLE_FILE="scripts/gcs-lifecycle.json"
PROJECT_ID="docket-java"

if [[ ! -f "${LIFECYCLE_FILE}" ]]; then
  echo "ERROR: Lifecycle file not found: ${LIFECYCLE_FILE}"
  exit 1
fi

echo "Current lifecycle configuration:"
gsutil lifecycle get "gs://${BUCKET}" || echo "  (none set)"

echo ""
echo "Applying new lifecycle configuration from ${LIFECYCLE_FILE}..."
gsutil lifecycle set "${LIFECYCLE_FILE}" "gs://${BUCKET}"

echo ""
echo "Verifying applied configuration:"
gsutil lifecycle get "gs://${BUCKET}"

echo ""
echo "✅ Lifecycle configuration applied successfully"
echo ""
echo "Summary of rules:"
echo "  1. Delete noncurrent versions after 90 days"
echo "  2. Abort incomplete multipart uploads after 7 days"
echo "  3. Transition live objects to Coldline after 365 days"