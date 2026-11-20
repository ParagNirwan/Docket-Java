#!/usr/bin/env bash
# Regional failover: promotes the DR replica to a standalone primary and repoints the
# backend's Cloud SQL proxy. ONE-WAY: the old primary can't rejoin; you rebuild a new
# replica afterwards. Follow docs/disaster-recovery.md; this script asks before each step.
#   PROJECT=docket-java ./scripts/dr-failover.sh
set -euo pipefail

PROJECT="${PROJECT:?set PROJECT}"
REPLICA="${REPLICA:-docket-db-dr-euw4}"
DR_REGION="${DR_REGION:-europe-west4}"
NAMESPACE="${NAMESPACE:-docket}"

confirm() { read -r -p "$1 [type yes] " answer; [[ "$answer" == "yes" ]] || { echo "Stopped."; exit 1; }; }

echo "Replica state:"
gcloud sql instances describe "$REPLICA" --project "$PROJECT" --format='table(name,region,state,replicaConfiguration.failoverTarget)'

confirm "1/3 Promote $REPLICA to primary? Writes to the old primary after this are lost."
gcloud sql instances promote-replica "$REPLICA" --project "$PROJECT"

confirm "2/3 Repoint the backend's Cloud SQL proxy to $PROJECT:$DR_REGION:$REPLICA?"
kubectl -n "$NAMESPACE" patch deployment backend --type json -p "[
  {\"op\":\"replace\",\"path\":\"/spec/template/spec/containers/1/args/1\",\"value\":\"$PROJECT:$DR_REGION:$REPLICA\"}
]"
kubectl -n "$NAMESPACE" rollout status deployment/backend --timeout=300s

confirm "3/3 Enable automated backups + PITR on the new primary?"
gcloud sql instances patch "$REPLICA" --project "$PROJECT" \
  --backup-start-time 03:00 --enable-point-in-time-recovery --retained-backups-count 30

echo "Failover done. Now: update k8s/base/backend-deployment.yaml with the new instance name"
echo "(so the next deploy doesn't revert it), and create a new replica from the new primary."
