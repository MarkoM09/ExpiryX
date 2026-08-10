package com.expiryx.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

/**
 * FUNCTIONALITY: Custom UI component that renders a semi-transparent dark overlay 
 * with a clear, rounded-rect cutout for the barcode scanning viewport.
 * USE OF DATA: Computes dynamic 'RectF' coordinates (Float) for the central reticle 
 * based on view dimensions and device screen density.
 * USE OF CODE STRUCTURES: Extends 'View'; overrides 'onDraw' to execute a sequence 
 * of Canvas drawing operations using custom 'Paint' and 'Xfermode' configurations.
 */
class ScannerMaskView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maskPaint = Paint().apply {
        color = Color.parseColor("#99000000") // Semi-transparent dark
        style = Paint.Style.FILL
    }

    private val transparentPaint = Paint().apply {
        color = Color.TRANSPARENT
        // USE OF CODE STRUCTURES: Xfermode configuration to 'punch through' the existing mask paint
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val frameRect = RectF()
    private val cornerRadius = 10f * resources.displayMetrics.density // match scan_frame_border corners

    /**
     * FUNCTIONALITY: Manually renders the graphics layers for the scanner UI.
     * USE OF DATA: Uses 'width' and 'height' (Float) of the view to center the reticle.
     * USE OF CODE STRUCTURES: Sequential rendering path: Draw background -> calculate 
     * frame bounds -> Draw clear cutout.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw full screen dark mask
        // CODE STRUCTURE: Drawing the base semi-transparent overlay across the full view area
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), maskPaint)

        // 2. Draw clear cutout in center
        // DATA: Arithmetic calculation to ensure the scanning frame is perfectly centered
        val frameWidth = 300f * resources.displayMetrics.density
        val frameHeight = 300f * resources.displayMetrics.density
        
        val left = (width - frameWidth) / 2f
        val top = (height - frameHeight) / 2f
        frameRect.set(left, top, left + frameWidth, top + frameHeight)

        // CODE STRUCTURE: Executing the clearing operation to create the scanning 'window'
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, transparentPaint)
    }
}