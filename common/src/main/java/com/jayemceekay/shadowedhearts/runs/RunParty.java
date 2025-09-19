package com.jayemceekay.shadowedhearts.runs;

import java.util.Set;
import java.util.UUID;

/** Minimal party representation using player UUIDs. */
public record RunParty(Set<UUID> members) {}
