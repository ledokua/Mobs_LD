package net.ledok.mobs_ld.entity.threat;

/** Implemented by mobs that track per-player aggro and target by threat. */
public interface ThreatHolder {
    ThreatTable getThreatTable();
}
