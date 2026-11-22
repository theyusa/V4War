package io.nekohasekai.sagernet.ui.widget

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.card.MaterialCardView

/** MaterialCardView that enforces a 1:1 square aspect ratio from its measured width. */
class SquareMaterialCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialCardViewStyle,
) : MaterialCardView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        if (widthMode != MeasureSpec.UNSPECIFIED && widthSize > 0) {
            val squareSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY)
            super.onMeasure(squareSpec, squareSpec)
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
}
