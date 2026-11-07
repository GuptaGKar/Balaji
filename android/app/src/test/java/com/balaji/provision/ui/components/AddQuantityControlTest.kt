package com.balaji.provision.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the ADD to stepper animation.
 *
 * The animation itself cannot be asserted from a JVM test, and pretending otherwise would be a
 * claim with no evidence behind it. What can be pinned is the three decisions the animation is
 * built from - which control to show, which way the digit travels, and what the digit says - and
 * those are exactly the ones that break when someone changes how a quantity is stored.
 */
class AddQuantityControlTest {

    @Test
    fun `zero collapses to ADD and any quantity shows the stepper`() {
        assertEquals(AddControlMode.Add, addControlModeFor(0L))
        assertEquals(AddControlMode.Stepper, addControlModeFor(1L))
        // A weighted line can sit at 250 g, which is a real quantity, not zero.
        assertEquals(AddControlMode.Stepper, addControlModeFor(250L))
    }

    @Test
    fun `a repeated quantity animates nothing`() {
        // The cart re-emits the same value on every refresh. If this returned a direction, every
        // refresh would slide a digit that had not changed.
        assertEquals(QuantitySlide.None, quantitySlide(2L, 2L))
        assertEquals(QuantitySlide.Increase, quantitySlide(2L, 3L))
        assertEquals(QuantitySlide.Decrease, quantitySlide(3L, 2L))
    }

    @Test
    fun `an increase enters from the right and a decrease from the left`() {
        assertEquals(100, digitSlideOffset(QuantitySlide.Increase, 100))
        assertEquals(-100, digitSlideOffset(QuantitySlide.Decrease, 100))
        assertEquals(0, digitSlideOffset(QuantitySlide.None, 100))
    }

    @Test
    fun `the digit counts steps, not base units`() {
        // A 500 g packet at two packs is 1000 base units; showing "1000" in a stepper whose plus
        // adds one pack would be a lie about what the button does.
        assertEquals(2L, stepCount(quantityBase = 1000L, stepQty = 500L))
        assertEquals(1L, stepCount(quantityBase = 1L, stepQty = 1L))
    }

    @Test
    fun `a quantity that is not a whole number of steps is shown as it is`() {
        // 470 g of a 250 g-minimum item is a real weighted line. Rounding it to 2 steps would
        // report a basket that does not exist.
        assertEquals(470L, stepCount(quantityBase = 470L, stepQty = 250L))
        // A zero step must not divide.
        assertEquals(250L, stepCount(quantityBase = 250L, stepQty = 0L))
    }

    @Test
    fun `the mode and the digit agree at zero`() {
        val mode = addControlModeFor(0L)
        assertEquals(AddControlMode.Add, mode)
        // Nothing should be mid-animation when the control is showing ADD.
        assertEquals(QuantitySlide.None, quantitySlide(0L, 0L))
        assertTrue(stepCount(0L, 250L) == 0L)
    }

    @Test
    fun `a slide that reaches the threshold commits and a shorter one does not`() {
        assertTrue(shouldCommit(offsetPx = 85f, travelPx = 100f, commitFraction = 0.85f))
        assertFalse(shouldCommit(offsetPx = 84f, travelPx = 100f, commitFraction = 0.85f))
        assertTrue(shouldCommit(offsetPx = 100f, travelPx = 100f, commitFraction = 0.85f))
    }

    @Test
    fun `an unmeasured track never reads as committed`() {
        // The first frame has not laid the row out yet. travelPx is 0, and dividing by it would
        // produce NaN, which compares greater than nothing but would still be wrong to act on.
        assertEquals(0f, slideProgress(offsetPx = 40f, travelPx = 0f))
        assertFalse(shouldCommit(offsetPx = 40f, travelPx = 0f, commitFraction = 0.85f))
    }

    @Test
    fun `progress is clamped to the track in both directions`() {
        assertEquals(1f, slideProgress(offsetPx = 250f, travelPx = 100f))
        assertEquals(0f, slideProgress(offsetPx = -250f, travelPx = 100f))
        assertEquals(0.5f, slideProgress(offsetPx = 50f, travelPx = 100f))
    }

    @Test
    fun `the caption only changes to the success wording once the caller says so`() {
        assertEquals(
            "Slide to Place Order",
            slideLabelFor(SlideConfirmState.Idle, "Slide to Place Order", "Order placed")
        )
        assertEquals(
            "Slide to Place Order",
            slideLabelFor(SlideConfirmState.Submitting, "Slide to Place Order", "Order placed")
        )
        // Submitting must not read as done: the server has not answered yet.
        assertEquals(
            "Order placed",
            slideLabelFor(SlideConfirmState.Success, "Slide to Place Order", "Order placed")
        )
        // A caller with no success wording keeps the label rather than showing an empty pill.
        assertEquals(
            "Slide to Checkout",
            slideLabelFor(SlideConfirmState.Success, "Slide to Checkout", null)
        )
    }
}
