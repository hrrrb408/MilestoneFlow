package com.milestoneflow.shared.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Field-level validation error matching architecture spec §10.1.
 *
 * @param field   the JSON path of the invalid field (e.g. "paymentSchedule[0].amount")
 * @param code    stable error code (e.g. "AMOUNT_MUST_BE_POSITIVE")
 * @param message human-readable message for display
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorDetail(String field, String code, String message) {}
