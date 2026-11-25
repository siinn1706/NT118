package vn.uit.healthos.core.designsystem.component

import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveFitTest {

    @Test
    fun twoTilesShareARowWhenBothFit() {
        // 359px wide, 12px gap: each cell is 173px.
        assertEquals(2, AdaptiveFit.columnCount(available = 359, spacing = 12, widestChild = 173, childCount = 2, maxColumns = 2))
    }

    @Test
    fun widestTileTooWideFallsBackToOneColumn() {
        assertEquals(1, AdaptiveFit.columnCount(available = 359, spacing = 12, widestChild = 174, childCount = 2, maxColumns = 2))
    }

    @Test
    fun threeItemsDropToTwoColumnsBeforeOne() {
        // Three cells of 112px are too narrow for 150px, two cells of 174px are not.
        assertEquals(2, AdaptiveFit.columnCount(available = 360, spacing = 12, widestChild = 150, childCount = 3, maxColumns = 3))
        assertEquals(1, AdaptiveFit.columnCount(available = 360, spacing = 12, widestChild = 200, childCount = 3, maxColumns = 3))
        assertEquals(3, AdaptiveFit.columnCount(available = 360, spacing = 12, widestChild = 100, childCount = 3, maxColumns = 3))
    }

    @Test
    fun columnCountNeverExceedsChildrenOrLimit() {
        assertEquals(1, AdaptiveFit.columnCount(available = 1000, spacing = 12, widestChild = 10, childCount = 1, maxColumns = 4))
        assertEquals(2, AdaptiveFit.columnCount(available = 1000, spacing = 12, widestChild = 10, childCount = 5, maxColumns = 2))
    }

    @Test
    fun unboundedWidthKeepsEveryColumnAndRow() {
        assertEquals(3, AdaptiveFit.columnCount(Constraints.Infinity, 12, 5000, childCount = 3, maxColumns = 3))
        assertTrue(AdaptiveFit.fitsInRow(Constraints.Infinity, 12, fixedWidth = 5000, fixedCount = 1, flexibleWidth = 5000, minFlexibleFraction = 1f))
    }

    @Test
    fun rowFitsWhenFlexibleChildStaysUnwrapped() {
        // 300px row, 100px action, 12px gap: 188px left for a 180px title.
        assertTrue(AdaptiveFit.fitsInRow(300, 12, fixedWidth = 100, fixedCount = 1, flexibleWidth = 180, minFlexibleFraction = 1f))
        assertFalse(AdaptiveFit.fitsInRow(300, 12, fixedWidth = 100, fixedCount = 1, flexibleWidth = 189, minFlexibleFraction = 1f))
    }

    @Test
    fun wrappingTextNeedsItsMinimumShare() {
        // Long text that wraps anyway must still get 60% of 300px = 180px.
        assertTrue(AdaptiveFit.fitsInRow(300, 12, fixedWidth = 108, fixedCount = 1, flexibleWidth = 900, minFlexibleFraction = 0.6f))
        assertFalse(AdaptiveFit.fitsInRow(300, 12, fixedWidth = 109, fixedCount = 1, flexibleWidth = 900, minFlexibleFraction = 0.6f))
    }

    @Test
    fun noRoomLeftNeverFits() {
        assertFalse(AdaptiveFit.fitsInRow(300, 12, fixedWidth = 288, fixedCount = 1, flexibleWidth = 0, minFlexibleFraction = 0f))
    }

    @Test
    fun cellWidthSplitsAfterGaps() {
        assertEquals(173, AdaptiveFit.cellWidth(359, 12, 2))
        assertEquals(0, AdaptiveFit.cellWidth(10, 12, 2))
    }
}
