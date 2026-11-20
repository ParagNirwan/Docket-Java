# Disaster recovery: regional failover

Complements [backup-recovery.md](backup-recovery.md), which covers same-region restores
(PITR, accidental deletes). This runbook covers losing **europe-west3** entirely.

## Topology

| Component | Primary | DR |
| :--- | :--- | :--- |
| Cloud SQL | `docket-db` (europe-west3) | `docket-db-dr-euw4`, a cross-region read replica in europe-west4, same tier |
| Documents (GCS) | `docket-java-uploads` (europe-west3) | Make the bucket dual-region (`EUR4`) or add Storage Transfer to a europe-west4 bucket (see below) |
| Images | Artifact Registry europe-west3 | Mirror to a europe-west4 repository, or rebuild from git |
| GKE | `autopilot-cluster-1` (europe-west3) | Create on demand in europe-west4 (Autopilot ~10 min) |

europe-west4 keeps everything inside the EU, so EU data residency still holds after failover.

**Targets:** RPO is the replica lag plus the GCS replication delay, normally under a minute.
RTO is about 45 minutes, most of it creating the cluster if there isn't a warm one.

## One-time setup

```bash
PROJECT=docket-java ./scripts/dr-create-replica.sh
```

- Alert on `cloudsql.googleapis.com/database/replication/replica_lag` > 60 s.
- **Documents.** GCS can't change a bucket's location in place. Either create a new dual-region
  bucket (`--location=EU --placement=europe-west3,europe-west4`), copy the objects and point
  `docket.storage.gcs.bucket` at it, or set up a daily Storage Transfer job to a europe-west4 bucket
  (RPO 24 h for files).
- With CMEK ([data-residency.md](data-residency.md)), create the KMS key in a location the DR
  region can use (a multi-region `europe` key ring, or a second key in europe-west4).
- Store a copy of the Secret Manager secrets in europe-west4 (replication policy `automatic`
  or user-managed with both regions).

## Failover procedure

1. **Declare.** Confirm the outage on the Google Cloud status page. Don't fail over for a blip:
   promotion is one-way.
2. **Freeze writes** if the primary is partly reachable: scale the backend to 0
   (`kubectl -n docket scale deployment backend --replicas=0`), so no writes land on the old primary.
3. **Compute.** If the europe-west3 cluster is gone, create one in europe-west4, then
   `kustomize build k8s/prod | kubectl apply -f -` with the europe-west4 images.
4. **Database.** Run `PROJECT=docket-java ./scripts/dr-failover.sh`. It promotes the replica,
   repoints the backend's Cloud SQL proxy sidecar and enables backups and PITR on the new primary,
   asking for confirmation at each step.
5. **Storage.** If the bucket isn't dual-region, set `docket.storage.gcs.bucket` to the
   europe-west4 copy.
6. **Traffic.** Point DNS (or the global load balancer backend) at the new gateway IP.
7. **Verify:** health endpoints, sign-in, upload, download, AI search, and the audit log has
   recent rows.
8. **Make it permanent.** Commit the new instance name in `k8s/base/backend-deployment.yaml`
   (the proxy args) so the next deploy doesn't revert it.

## Failing back

There's no automatic failback. Once europe-west3 is healthy, create a replica of the *new*
primary in europe-west3, wait for lag ≈ 0, then repeat the procedure in the other direction
during a maintenance window. Delete the old `docket-db` only after a successful backup of the
new primary.

## Drill

Twice a year, in a separate project or with a throwaway replica: create a replica, promote it,
point a staging deployment at it and run the smoke tests. Record timings against the targets.
