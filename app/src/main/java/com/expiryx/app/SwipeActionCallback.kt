package com.expiryx.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * FUNCTIONALITY: Implements swipe-to-action behaviors for RecyclerView items, 
 * providing visual feedback (colors/icons) and triggering callbacks for left and right swipes.
 * USE OF DATA: Manages 'String' labels, 'Int' color and resource IDs, and 'onSwipe' lambda callbacks. 
 * Reads item dimensions (Float) to perform custom Canvas drawing.
 * USE OF CODE STRUCTURES: Extends 'ItemTouchHelper.SimpleCallback', overrides 'onSwiped' 
 * and 'onChildDraw', and uses 'if/else' selection for directional gesture handling.
 */
class SwipeActionCallback(
    private val context: Context,
    private val leftLabel: String,
    private val leftColor: Int,
    private val leftIconRes: Int,
    private val rightLabel: String,
    private val rightColor: Int,
    private val rightIconRes: Int,
    private val onSwipeLeft: (Int) -> Unit,
    private val onSwipeRight: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    private val paint = Paint()
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    private val leftIcon: Drawable? = ContextCompat.getDrawable(context, leftIconRes)
    private val rightIcon: Drawable? = ContextCompat.getDrawable(context, rightIconRes)

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    /**
     * FUNCTIONALITY: Callback triggered when a swipe gesture is completed in either direction.
     * USE OF DATA: Accepts 'direction' (Int) and 'viewHolder'. Triggers lambda with adapter position (Int).
     * USE OF CODE STRUCTURES: Selection structure (if/else) to branch logic based on swipe direction.
     */
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // CODE STRUCTURE: Branching between two different user actions (e.g., Mark Used vs Edit)
        if (direction == ItemTouchHelper.LEFT) {
            onSwipeLeft(viewHolder.bindingAdapterPosition)
        } else {
            onSwipeRight(viewHolder.bindingAdapterPosition)
        }
    }

    /**
     * FUNCTIONALITY: Manually draws the background color, icon, and text label 
     * behind the swiped item.
     * USE OF DATA: Consumes 'Canvas' for drawing and 'dX' (Float) for current swipe offset.
     * USE OF CODE STRUCTURES: Selection structure (if/else) evaluating 'dX' sign 
     * to determine which side of the item to draw.
     */
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top
        
        // DATA: Calculation of icon size based on device density metrics
        val density = context.resources.displayMetrics.density
        val iconSize = (24 * density).toInt()

        // CODE STRUCTURE: Selection structure for rendering swiping-left visual feedback
        if (dX < 0) { // Swiping Left
            paint.color = rightColor
            val background = RectF(
                itemView.right.toFloat() + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
            c.drawRect(background, paint)

            rightIcon?.let {
                // DATA: Geometric calculations for centering icons within the swiped row
                val iconMargin = (itemHeight - (iconSize + 40)) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + iconSize
                val iconRight = itemView.right - (16 * density)
                val iconLeft = iconRight - iconSize
                it.setBounds(iconLeft.toInt(), iconTop.toInt(), iconRight.toInt(), iconBottom.toInt())
                it.setTint(Color.WHITE)
                it.draw(c)
                
                c.drawText(rightLabel, (iconLeft + iconRight) / 2f, iconBottom + 30f, textPaint)
            }

        } else if (dX > 0) { // Swiping Right
            // CODE STRUCTURE: Selection structure for rendering swiping-right visual feedback
            paint.color = leftColor
            val background = RectF(
                itemView.left.toFloat(),
                itemView.top.toFloat(),
                itemView.left.toFloat() + dX,
                itemView.bottom.toFloat()
            )
            c.drawRect(background, paint)

            leftIcon?.let {
                val iconMargin = (itemHeight - (iconSize + 40)) / 2
                val iconTop = itemView.top + iconMargin
                val iconBottom = iconTop + iconSize
                val iconLeft = itemView.left + (16 * density)
                val iconRight = iconLeft + iconSize
                it.setBounds(iconLeft.toInt(), iconTop.toInt(), iconRight.toInt(), iconBottom.toInt())
                it.setTint(Color.WHITE)
                it.draw(c)
                
                c.drawText(leftLabel, (iconLeft + iconRight) / 2f, iconBottom + 30f, textPaint)
            }
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}