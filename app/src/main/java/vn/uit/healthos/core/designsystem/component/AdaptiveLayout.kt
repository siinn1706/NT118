package vn.uit.healthos.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure fit rules behind [AdaptiveRow] and [AdaptiveColumns], kept free of Compose so they can be unit tested. Widths
 * are in pixels; [Constraints.Infinity] as the available width means there is no limit (inside a horizontal scroll).
 */
internal object AdaptiveFit {

    /**
     * Largest number of equal columns, at most [maxColumns] and [childCount], whose cell holds [widestChild] (the
     * widest child laid out without wrapping). Falls back to 1, a single full-width column.
     */
    fun columnCount(available: Int, spacing: Int, widestChild: Int, childCount: Int, maxColumns: Int): Int {
        val limit = minOf(childCount, maxColumns)
        if (limit <= 1) return 1
        if (available == Constraints.Infinity) return limit
        for (columns in limit downTo 2) {
            if (widestChild <= cellWidth(available, spacing, columns)) return columns
        }
        return 1
    }

    /** Width of one of [columns] equal cells separated by [spacing]. */
    fun cellWidth(available: Int, spacing: Int, columns: Int): Int =
        ((available - spacing * (columns - 1)) / columns).coerceAtLeast(0)

    /**
     * Whether a row keeps its children side by side: the fixed children take [fixedWidth] (their unwrapped widths
     * summed) plus one [spacing] each, and the flexible child gets the rest. That rest must hold the flexible child
     * unwrapped ([flexibleWidth]) or, when it has to wrap anyway, at least [minFlexibleFraction] of the row, so the
     * text never shrinks to a column of single words. A fraction of 1 means the flexible child must fit unwrapped.
     */
    fun fitsInRow(
        available: Int,
        spacing: Int,
        fixedWidth: Int,
        fixedCount: Int,
        flexibleWidth: Int,
        minFlexibleFraction: Float,
    ): Boolean {
        if (available == Constraints.Infinity) return true
        val remaining = available - fixedWidth - spacing * fixedCount
        if (remaining <= 0) return false
        val needed = minOf(flexibleWidth, (available * minFlexibleFraction.coerceIn(0f, 1f)).toInt())
        return remaining >= needed
    }
}

/**
 * Row that stays on one line only while it reads well, and otherwise stacks its children vertically, in the same
 * reading order. The child at [flexibleIndex] (usually the text) takes the width left by the others, which keep their
 * natural width; the row stays horizontal when that leftover holds the flexible child unwrapped, or at least
 * [minFlexibleFraction] of the row (pass 1f to require the flexible child on a single line, for a title beside an
 * action). Use it for a title with a trailing action, a value beside its label, or an icon beside a block of text.
 *
 * Stacked children are separated by [verticalSpacing] and aligned with [stackedAlignment]. The decision uses
 * intrinsic widths, so it runs only when the content or the constraints change, never per frame. Children must
 * support intrinsic measurement (no lazy lists or BoxWithConstraints inside).
 *
 * The intrinsic widths this row reports describe its stacked form (the widest child), so a parent choosing between
 * columns, such as [AdaptiveColumns], asks how narrow it can go without breaking words; given more room the row puts
 * its children on one line itself.
 */
@Composable
fun AdaptiveRow(
    modifier: Modifier = Modifier,
    flexibleIndex: Int = 0,
    minFlexibleFraction: Float = 0.6f,
    spacing: Dp = 12.dp,
    verticalSpacing: Dp = 8.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    stackedAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    val policy = remember(flexibleIndex, minFlexibleFraction, spacing, verticalSpacing, verticalAlignment, stackedAlignment) {
        AdaptiveRowPolicy(flexibleIndex, minFlexibleFraction, spacing, verticalSpacing, verticalAlignment, stackedAlignment)
    }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

/**
 * Equal-width grid that uses as many columns as fit, up to [maxColumns]: a column count is kept only when every
 * child fits its cell without wrapping (by its intrinsic width), so two tiles sit side by side on a roomy screen and
 * turn into one full-width column with a large font. Children are laid out in reading order, row by row, and get
 * exactly the cell width. With [equalHeight], children in the same row are stretched to the tallest one.
 */
@Composable
fun AdaptiveColumns(
    modifier: Modifier = Modifier,
    maxColumns: Int = Int.MAX_VALUE,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = horizontalSpacing,
    equalHeight: Boolean = false,
    content: @Composable () -> Unit,
) {
    val policy = remember(maxColumns, horizontalSpacing, verticalSpacing, equalHeight) {
        AdaptiveColumnsPolicy(maxColumns, horizontalSpacing, verticalSpacing, equalHeight)
    }
    Layout(content = content, modifier = modifier, measurePolicy = policy)
}

private class AdaptiveRowPolicy(
    private val flexibleIndex: Int,
    private val minFlexibleFraction: Float,
    private val spacing: Dp,
    private val verticalSpacing: Dp,
    private val verticalAlignment: Alignment.Vertical,
    private val stackedAlignment: Alignment.Horizontal,
) : MeasurePolicy {

    private fun IntrinsicMeasureScope.fits(children: List<IntrinsicMeasurable>, available: Int): Boolean {
        if (children.size <= 1) return true
        val flex = flexibleIndex.coerceIn(0, children.lastIndex)
        var fixedWidth = 0
        children.forEachIndexed { index, child ->
            if (index != flex) fixedWidth += child.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(available)
        }
        return AdaptiveFit.fitsInRow(
            available = available,
            spacing = spacing.roundToPx(),
            fixedWidth = fixedWidth,
            fixedCount = children.size - 1,
            flexibleWidth = children[flex].maxIntrinsicWidth(Constraints.Infinity),
            minFlexibleFraction = minFlexibleFraction,
        )
    }

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        if (measurables.isEmpty()) return layout(constraints.minWidth, constraints.minHeight) {}
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        return if (fits(measurables, available)) {
            measureRow(measurables, constraints, available)
        } else {
            measureStacked(measurables, constraints)
        }
    }

    private fun MeasureScope.measureRow(
        measurables: List<Measurable>,
        constraints: Constraints,
        available: Int,
    ): MeasureResult {
        val gap = spacing.roundToPx()
        val flex = flexibleIndex.coerceIn(0, measurables.lastIndex)
        val childConstraints = Constraints(maxHeight = constraints.maxHeight)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var used = gap * (measurables.size - 1)
        measurables.forEachIndexed { index, child ->
            if (index != flex) {
                val maxWidth = if (available == Constraints.Infinity) Constraints.Infinity else (available - used).coerceAtLeast(0)
                val placeable = child.measure(childConstraints.copy(maxWidth = maxWidth))
                placeables[index] = placeable
                used += placeable.width
            }
        }
        val flexSlot = if (available == Constraints.Infinity) {
            measurables[flex].maxIntrinsicWidth(Constraints.Infinity)
        } else {
            (available - used).coerceAtLeast(0)
        }
        placeables[flex] = measurables[flex].measure(childConstraints.copy(maxWidth = flexSlot))
        val width = if (available == Constraints.Infinity) used + flexSlot else available
        val height = placeables.maxOf { it!!.height }.coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            var x = 0
            placeables.forEachIndexed { index, placeable ->
                placeable!!
                placeable.placeRelative(x, verticalAlignment.align(placeable.height, height))
                x += (if (index == flex) flexSlot else placeable.width) + gap
            }
        }
    }

    private fun MeasureScope.measureStacked(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val gap = verticalSpacing.roundToPx()
        val childConstraints = Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(childConstraints) }
        val width = constraints.maxWidth
        val height = (placeables.sumOf { it.height } + gap * (placeables.size - 1))
            .coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width, height) {
            var y = 0
            placeables.forEach { placeable ->
                placeable.place(stackedAlignment.align(placeable.width, width, layoutDirection), y)
                y += placeable.height + gap
            }
        }
    }

    // Intrinsic widths describe the stacked form: the widest child.
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        intrinsicHeight(measurables, width) { child, w -> child.minIntrinsicHeight(w) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        intrinsicHeight(measurables, width) { child, w -> child.maxIntrinsicHeight(w) }

    private inline fun IntrinsicMeasureScope.intrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
        heightOf: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        if (measurables.isEmpty()) return 0
        if (!fits(measurables, width)) {
            return measurables.sumOf { heightOf(it, width) } + verticalSpacing.roundToPx() * (measurables.size - 1)
        }
        if (width == Constraints.Infinity) return measurables.maxOf { heightOf(it, Constraints.Infinity) }
        val flex = flexibleIndex.coerceIn(0, measurables.lastIndex)
        var rest = width - spacing.roundToPx() * (measurables.size - 1)
        var tallest = 0
        measurables.forEachIndexed { index, child ->
            if (index != flex) {
                val childWidth = child.maxIntrinsicWidth(Constraints.Infinity).coerceAtMost(rest.coerceAtLeast(0))
                rest -= childWidth
                tallest = maxOf(tallest, heightOf(child, childWidth))
            }
        }
        return maxOf(tallest, heightOf(measurables[flex], rest.coerceAtLeast(0)))
    }
}

private class AdaptiveColumnsPolicy(
    private val maxColumns: Int,
    private val horizontalSpacing: Dp,
    private val verticalSpacing: Dp,
    private val equalHeight: Boolean,
) : MeasurePolicy {

    private fun IntrinsicMeasureScope.columns(children: List<IntrinsicMeasurable>, available: Int): Int =
        AdaptiveFit.columnCount(
            available = available,
            spacing = horizontalSpacing.roundToPx(),
            widestChild = children.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) },
            childCount = children.size,
            maxColumns = maxColumns,
        )

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        if (measurables.isEmpty()) return layout(constraints.minWidth, constraints.minHeight) {}
        val hGap = horizontalSpacing.roundToPx()
        val vGap = verticalSpacing.roundToPx()
        val available = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
        val columns = columns(measurables, available)
        val width = if (available == Constraints.Infinity) {
            measurables.maxOf { it.maxIntrinsicWidth(Constraints.Infinity) } * columns + hGap * (columns - 1)
        } else {
            available
        }
        val cell = AdaptiveFit.cellWidth(width, hGap, columns)
        val rows = measurables.chunked(columns).map { row ->
            val rowMin = if (equalHeight && row.size > 1) {
                row.maxOf { it.minIntrinsicHeight(cell) }.coerceAtMost(constraints.maxHeight)
            } else {
                0
            }
            row.map { it.measure(Constraints(minWidth = cell, maxWidth = cell, minHeight = rowMin, maxHeight = constraints.maxHeight)) }
        }
        val rowHeights = rows.map { row -> row.maxOf { it.height } }
        val height = (rowHeights.sum() + vGap * (rows.size - 1)).coerceIn(constraints.minHeight, constraints.maxHeight)
        return layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            var y = 0
            rows.forEachIndexed { rowIndex, row ->
                row.forEachIndexed { column, placeable -> placeable.placeRelative(column * (cell + hGap), y) }
                y += rowHeights[rowIndex] + vGap
            }
        }
    }

    // Intrinsic widths describe the single-column form: the widest child.
    override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.maxIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        measurables.maxOfOrNull { it.minIntrinsicWidth(height) } ?: 0

    override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        intrinsicHeight(measurables, width) { child, w -> child.minIntrinsicHeight(w) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        intrinsicHeight(measurables, width) { child, w -> child.maxIntrinsicHeight(w) }

    private inline fun IntrinsicMeasureScope.intrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
        heightOf: (IntrinsicMeasurable, Int) -> Int,
    ): Int {
        if (measurables.isEmpty()) return 0
        val columns = columns(measurables, width)
        val cell = if (width == Constraints.Infinity) Constraints.Infinity else AdaptiveFit.cellWidth(width, horizontalSpacing.roundToPx(), columns)
        val rows = measurables.chunked(columns)
        return rows.sumOf { row -> row.maxOf { heightOf(it, cell) } } + verticalSpacing.roundToPx() * (rows.size - 1)
    }
}
