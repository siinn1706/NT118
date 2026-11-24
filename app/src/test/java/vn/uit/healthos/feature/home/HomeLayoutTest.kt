package vn.uit.healthos.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {

    @Test
    fun `default layout shows every tile in declaration order`() {
        val layout = HomeLayout.decode(null, null)

        assertEquals(HomeTile.entries, layout.order)
        assertEquals(HomeTile.entries, layout.visible)
    }

    @Test
    fun `decode drops unknown and duplicate ids and appends tiles it does not know`() {
        val layout = HomeLayout.decode("water,removed_tile,steps,water", "climate,bogus")

        assertEquals(listOf(HomeTile.WATER, HomeTile.STEPS), layout.order.take(2))
        assertEquals(HomeTile.entries.size, layout.order.size)
        assertEquals(setOf(HomeTile.CLIMATE), layout.hidden)
        assertFalse(HomeTile.CLIMATE in layout.visible)
    }

    @Test
    fun `encode and decode round trip`() {
        val layout = HomeLayout()
            .moved(HomeTile.WEEKLY_CHART, -100)
            .toggled(HomeTile.EXERCISES)
            .toggled(HomeTile.VITAL)

        val restored = HomeLayout.decode(layout.encodeOrder(), layout.encodeHidden())

        assertEquals(layout, restored)
        assertEquals(HomeTile.WEEKLY_CHART, restored.order.first())
    }

    @Test
    fun `move is clamped to the list and toggling twice restores visibility`() {
        val layout = HomeLayout()

        assertEquals(layout, layout.moved(HomeTile.VITAL, -1))
        assertEquals(HomeTile.VITAL, layout.moved(HomeTile.VITAL, 1).order[1])
        assertTrue(layout.toggled(HomeTile.STEPS).isHidden(HomeTile.STEPS))
        assertEquals(layout, layout.toggled(HomeTile.STEPS).toggled(HomeTile.STEPS))
    }

    @Test
    fun `half tiles pair up and a lone half tile takes a whole row`() {
        val rows = HomeLayout.rows(
            listOf(HomeTile.VITAL, HomeTile.STEPS, HomeTile.CALORIES, HomeTile.WATER, HomeTile.HEART_RATE),
        )

        assertEquals(
            listOf(
                listOf(HomeTile.VITAL, HomeTile.STEPS),
                listOf(HomeTile.CALORIES),
                listOf(HomeTile.WATER),
                listOf(HomeTile.HEART_RATE),
            ),
            rows,
        )
    }
}
