package com.milestoneflow.progress.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ProgressRateCalculator}.
 *
 * <p>Verifies completion rate calculation rules:
 * <ul>
 *   <li>0 tasks → 0.00%</li>
 *   <li>1/1 completed → 100.00%</li>
 *   <li>1/2 completed → 50.00%</li>
 *   <li>1/3 completed → 33.33%</li>
 *   <li>2/3 completed → 66.67%</li>
 *   <li>Result is always 2 decimal places</li>
 * </ul>
 */
@DisplayName("ProgressRateCalculator")
class ProgressRateCalculatorTest {

    @Nested
    @DisplayName("calculate")
    class Calculate {

        @Test
        @DisplayName("0 total tasks → 0.00")
        void zeroTasksReturnsZero() {
            assertThat(ProgressRateCalculator.calculate(0, 0))
                    .isEqualByComparingTo(BigDecimal.valueOf(0.00));
            assertThat(ProgressRateCalculator.calculate(0, 0).scale()).isEqualTo(2);
        }

        @Test
        @DisplayName("1/1 completed → 100.00")
        void allCompleted() {
            assertThat(ProgressRateCalculator.calculate(1, 1))
                    .isEqualByComparingTo(BigDecimal.valueOf(100.00));
        }

        @Test
        @DisplayName("1/2 completed → 50.00")
        void halfCompleted() {
            assertThat(ProgressRateCalculator.calculate(1, 2))
                    .isEqualByComparingTo(BigDecimal.valueOf(50.00));
        }

        @Test
        @DisplayName("1/3 completed → 33.33")
        void oneThirdCompleted() {
            assertThat(ProgressRateCalculator.calculate(1, 3))
                    .isEqualByComparingTo(BigDecimal.valueOf(33.33));
        }

        @Test
        @DisplayName("2/3 completed → 66.67")
        void twoThirdsCompleted() {
            assertThat(ProgressRateCalculator.calculate(2, 3))
                    .isEqualByComparingTo(BigDecimal.valueOf(66.67));
        }

        @Test
        @DisplayName("0/5 completed → 0.00")
        void noneCompleted() {
            assertThat(ProgressRateCalculator.calculate(0, 5))
                    .isEqualByComparingTo(BigDecimal.valueOf(0.00));
        }

        @Test
        @DisplayName("3/7 completed → 42.86")
        void mixedCompletion() {
            assertThat(ProgressRateCalculator.calculate(3, 7))
                    .isEqualByComparingTo(BigDecimal.valueOf(42.86));
        }

        @Test
        @DisplayName("4/10 completed → 40.00")
        void fortyPercent() {
            assertThat(ProgressRateCalculator.calculate(4, 10))
                    .isEqualByComparingTo(BigDecimal.valueOf(40.00));
        }

        @Test
        @DisplayName("1/6 completed → 16.67")
        void oneSixthCompleted() {
            assertThat(ProgressRateCalculator.calculate(1, 6))
                    .isEqualByComparingTo(BigDecimal.valueOf(16.67));
        }

        @Test
        @DisplayName("5/6 completed → 83.33")
        void fiveSixthsCompleted() {
            assertThat(ProgressRateCalculator.calculate(5, 6))
                    .isEqualByComparingTo(BigDecimal.valueOf(83.33));
        }

        @Test
        @DisplayName("0/3 completed → 0.00")
        void zeroCompletedWithTasks() {
            assertThat(ProgressRateCalculator.calculate(0, 3))
                    .isEqualByComparingTo(BigDecimal.valueOf(0.00));
        }

        @Test
        @DisplayName("result always has 2 decimal places")
        void resultScale() {
            assertThat(ProgressRateCalculator.calculate(0, 0).scale()).isEqualTo(2);
            assertThat(ProgressRateCalculator.calculate(1, 1).scale()).isEqualTo(2);
            assertThat(ProgressRateCalculator.calculate(1, 3).scale()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("boundary validation")
    class BoundaryValidation {

        @Test
        @DisplayName("negative completedTasks throws IllegalArgumentException")
        void negativeCompletedThrows() {
            assertThatThrownBy(() -> ProgressRateCalculator.calculate(-1, 3))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("negative totalTasks throws IllegalArgumentException")
        void negativeTotalThrows() {
            assertThatThrownBy(() -> ProgressRateCalculator.calculate(0, -1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("completedTasks greater than totalTasks throws IllegalArgumentException")
        void completedExceedsTotalThrows() {
            assertThatThrownBy(() -> ProgressRateCalculator.calculate(3, 2))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("completedTasks greater than zero totalTasks throws (never silently 0.00)")
        void completedWithZeroTotalThrows() {
            // Defends the invariant that completionRate can never exceed 100.00:
            // a completed count without a matching total is inconsistent source data.
            assertThatThrownBy(() -> ProgressRateCalculator.calculate(5, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("0/0 is still valid and returns 0.00")
        void zeroOverZeroIsValid() {
            assertThat(ProgressRateCalculator.calculate(0, 0))
                    .isEqualByComparingTo(BigDecimal.valueOf(0.00));
        }
    }
}
