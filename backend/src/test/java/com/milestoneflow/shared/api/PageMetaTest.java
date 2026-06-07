package com.milestoneflow.shared.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageMetaTest {

    @Test
    void shouldCreateValidPageMeta() {
        PageMeta meta = new PageMeta(0, 20, 100, 5, true);

        assertThat(meta.page()).isEqualTo(0);
        assertThat(meta.size()).isEqualTo(20);
        assertThat(meta.totalElements()).isEqualTo(100);
        assertThat(meta.totalPages()).isEqualTo(5);
        assertThat(meta.hasNext()).isTrue();
    }

    @Test
    void shouldComputeTotalPagesAndHasNextViaFactory() {
        PageMeta meta = PageMeta.of(0, 20, 100);

        assertThat(meta.totalPages()).isEqualTo(5);
        assertThat(meta.hasNext()).isTrue();
    }

    @Test
    void shouldComputeExactPageBoundary() {
        // 100 elements, page size 20 → 5 pages, page 4 is the last
        PageMeta lastPage = PageMeta.of(4, 20, 100);

        assertThat(lastPage.totalPages()).isEqualTo(5);
        assertThat(lastPage.hasNext()).isFalse();
    }

    @Test
    void shouldHandleEmptyResult() {
        PageMeta meta = PageMeta.of(0, 20, 0);

        assertThat(meta.totalElements()).isZero();
        assertThat(meta.totalPages()).isZero();
        assertThat(meta.hasNext()).isFalse();
    }

    @Test
    void shouldHandlePartialLastPage() {
        // 51 elements, page size 20 → 3 pages (20+20+11)
        PageMeta meta = PageMeta.of(2, 20, 51);

        assertThat(meta.totalPages()).isEqualTo(3);
        assertThat(meta.hasNext()).isFalse();
    }

    @Test
    void shouldHandleSingleElementResult() {
        PageMeta meta = PageMeta.of(0, 20, 1);

        assertThat(meta.totalPages()).isEqualTo(1);
        assertThat(meta.hasNext()).isFalse();
    }

    @Test
    void shouldRejectNegativePage() {
        assertThatThrownBy(() -> new PageMeta(-1, 20, 100, 5, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must be >= 0");
    }

    @Test
    void shouldRejectZeroSize() {
        assertThatThrownBy(() -> new PageMeta(0, 0, 100, 0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be > 0");
    }

    @Test
    void shouldRejectNegativeSize() {
        assertThatThrownBy(() -> new PageMeta(0, -1, 100, 0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be > 0");
    }

    @Test
    void shouldRejectNegativeTotalElements() {
        assertThatThrownBy(() -> new PageMeta(0, 20, -1, 0, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalElements must be >= 0");
    }

    @Test
    void shouldRejectNegativeTotalPages() {
        assertThatThrownBy(() -> new PageMeta(0, 20, 100, -1, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalPages must be >= 0");
    }

    @Test
    void shouldHaveHasNextTrueWhenMorePagesExist() {
        PageMeta meta = PageMeta.of(0, 10, 25);

        assertThat(meta.totalPages()).isEqualTo(3);
        assertThat(meta.hasNext()).isTrue();
    }

    @Test
    void shouldHaveHasNextFalseOnLastPage() {
        PageMeta meta = PageMeta.of(2, 10, 25);

        assertThat(meta.hasNext()).isFalse();
    }
}
