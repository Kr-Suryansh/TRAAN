package com.sih.data

/**
 * Authoritative constants for SOS lifecycle policy.
 *
 * All modules must reference these values instead of hardcoding magic numbers.
 * If the policy changes, update ONLY this file.
 *
 * TTL policy per contract: ~48–72 hours.
 * We use 72 hours (the upper bound) to maximise relay availability.
 * Records must NOT be deleted immediately after upload — other phones may still
 * need them from the relay mesh (rule G of the non-negotiable contract).
 */
object SosConstants {

    /** Time-to-live for uploaded SOS records, in hours. */
    const val TTL_HOURS: Long = 72L

    /** Time-to-live in seconds — use this for cutoff calculations. */
    const val TTL_SECONDS: Long = TTL_HOURS * 3600L
}
