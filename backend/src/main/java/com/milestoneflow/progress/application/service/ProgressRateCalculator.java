package com.milestoneflow.progress.application.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Stateless utility for computing task-based completion rates.
 *
 * <p>Rules (per B6-001 spec):
 * <ul>
 *   <li>{@code completionRate = completedTasks / totalTasks * 100}</li>
 *   <li>{@code totalTasks == 0} → {@code 0.00} (never 100%)</li>
 *   <li>Result is scaled to 2 decimal places with {@code HALF_UP} rounding</li>
 * </ul>
 */
final class ProgressRateCalculator {

    private static final MathContext MC = new MathContext(4, RoundingMode.HALF_UP);

    private ProgressRateCalculator() {
        // utility class
    }

    /**
     * Computes the completion rate as a percentage.
     *
     * @param completedTasks number of completed tasks
     * @param totalTasks     total number of tasks
     * @return rate in [0.00, 100.00]
     */
    static BigDecimal calculate(long completedTasks, long totalTasks) {
        if (totalTasks == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(completedTasks)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalTasks), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
