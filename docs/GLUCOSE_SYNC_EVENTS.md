# Glucose synchronization and calculation events

An upload acknowledgement can change only the server identifier and database
revision of an existing glucose record. Treating this as a new glucose value
replaces the running calculation chain, even though no clinical input changed.

The compatibility event publisher now keeps up to256 private clinical snapshots
per subscription. Only an already-known record with unchanged clinical content,
reference identity and a non-older revision avoids the duplicate glucose/history
events. It uses GlucoseValue.contentEqualsTo, including value, raw, validity,
timestamp, UTC offset, trend, noise and sensor type.

Unknown, evicted, unpersisted, out-of-order and clinically modified records still
trigger normal recalculation. Mutable caller entities are copied. Restarting the
subscription discards the snapshots and retains the initial reload/widget event.
Mixed batches retain the oldest and newest clinically changed glucose times.

This filter is local to CompatDBHelper. The repository observable, synchronization
acknowledgements, stored records, history retention and non-glucose events are not
changed. No calculation cancellation checks, insulin math, constraints, pump
commands, notification settings or delivery intervals are changed.

Regression tests cover both the comparison and the real Rx event publisher,
including independent synchronization subscribers, invalidations, corrections,
batch bounds, treatment events, mutation, eviction and subscription restart.
The CI workflow runs synthetic tests and lint without signing keys or deployment.
The runtime APK must additionally pass tests/build on its actual installed-source
branch; the public regression branch is not itself a phone release.
