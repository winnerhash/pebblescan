package io.github.g00fy2.quickie

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources.NotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff.Mode.CLEAR
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import io.github.g00fy2.quickie.databinding.QuickieOverlayViewBinding
import kotlin.math.min
import kotlin.math.roundToInt

@Suppress("TooManyFunctions")
internal class QROverlayView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

  private val binding = QuickieOverlayViewBinding.inflate(LayoutInflater.from(context), this)
  private val grayColor = ContextCompat.getColor(context, R.color.quickie_gray)
  private val accentColor = getAccentColor()
  private val backgroundColor = ColorUtils.setAlphaComponent(Color.BLACK, BACKGROUND_ALPHA.roundToInt())
  private val alphaPaint = Paint().apply { alpha = BACKGROUND_ALPHA.roundToInt() }
  private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val loadingBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backgroundColor }
  private val transparentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.TRANSPARENT
    xfermode = PorterDuffXfermode(CLEAR)
  }
  private val outerRadius = OUT_RADIUS.toPx()
  private val innerRadius = (OUT_RADIUS - STROKE_WIDTH).toPx()
  private val outerFrame = RectF()
  private val innerFrame = RectF()
  private var maskBitmap: Bitmap? = null
  private var maskCanvas: Canvas? = null
  private var horizontalFrameRatio = 1f
  private var focusAction: ((Float, Float) -> Unit)? = null
  private var focusTouch: Pair<Float, Float>? = null
  private var selectionFrozen = false
  private val zoomControls = ZoomControls(binding)
  private val codeMarkers = CodeMarkers(this, binding.codeMarkers)
  private val gestures = PreviewGestures(context) { x, y ->
    focusTouch = x to y
    performClick()
  }
  var isHighlighted = false
    set(value) {
      if (field != value) {
        field = value
        invalidate()
      }
    }
  var isLoading = false
    set(value) {
      if (field != value) {
        field = value
        binding.progressView.visibility = if (value) VISIBLE else GONE
      }
    }

  init {
    setWillNotDraw(false)
  }

  // PreviewGestures delivers single taps through performClick; pinches and drags never click.
  @SuppressLint("ClickableViewAccessibility")
  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!selectionFrozen) {
      if (focusAction == null) return super.onTouchEvent(event)
      gestures.onTouch(event)
    }
    return true
  }

  override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
    event.pointerCount > 1 || super.onInterceptTouchEvent(event)

  fun setCodeChoices(choices: List<CodeMarker>, onSelected: (Int) -> Unit) {
    binding.selectionActions.isVisible = choices.isNotEmpty()
    binding.selectionHint.isVisible = choices.isNotEmpty()
    codeMarkers.show(choices, onSelected)
  }

  fun setSelectionFrozen(frozen: Boolean, onResume: () -> Unit) {
    selectionFrozen = frozen
    binding.liveControls.isVisible = !frozen
    binding.resumeScanning.isVisible = frozen
    binding.selectionHint.setText(if (frozen) R.string.quickie_frame_held else R.string.quickie_tap_code)
    binding.resumeScanning.setOnClickListener { onResume() }
  }

  fun isCodeVisible(x: Float, y: Float): Boolean {
    val horizontal = x * width in paddingLeft.toFloat()..(width - paddingRight).toFloat()
    val vertical = y * height in paddingTop.toFloat()..(binding.bottomControls.top - 8f.toPx())
    return width > 0 && height > 0 && horizontal && vertical
  }

  override fun performClick(): Boolean {
    super.performClick()
    val point = focusTouch ?: (width / 2f to height / 2f)
    focusAction?.invoke(point.first, point.second)
    focusTouch = null
    return true
  }

  fun setFocusAction(action: (Float, Float) -> Unit) {
    focusAction = action
    isClickable = true
    isFocusable = true
    contentDescription = context.getString(R.string.quickie_focus)
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    super.onLayout(changed, left, top, right, bottom)

    if (width > 0 && height > 0) calculateFrameAndTitlePos()
  }

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    super.onSizeChanged(width, height, oldWidth, oldHeight)
    if (width > 0 && height > 0) {
      maskBitmap?.recycle()
      maskBitmap = createBitmap(width, height).apply { maskCanvas = Canvas(this) }
    }
  }

  @Suppress("UnsafeCallOnNullableType")
  override fun onDraw(canvas: Canvas) {
    strokePaint.color = if (isHighlighted) accentColor else grayColor
    maskCanvas!!.drawColor(backgroundColor)
    maskCanvas!!.drawRoundRect(outerFrame, outerRadius, outerRadius, strokePaint)
    maskCanvas!!.drawRoundRect(innerFrame, innerRadius, innerRadius, transparentPaint)
    if (isLoading) maskCanvas!!.drawRoundRect(innerFrame, innerRadius, innerRadius, loadingBackgroundPaint)
    canvas.drawBitmap(maskBitmap!!, 0f, 0f, alphaPaint)
    super.onDraw(canvas)
  }

  fun setCustomText(stringRes: Int) {
    if (stringRes != 0) {
      try {
        binding.titleTextView.setText(stringRes)
      } catch (ignore: NotFoundException) {
        // string resource not found
      }
    }
  }

  fun setCustomIcon(drawableRes: Int?) {
    if (drawableRes == null) {
      binding.titleTextView.setCompoundDrawables(null, null, null, null)
    } else if (drawableRes != 0) {
      try {
        ResourcesCompat.getDrawable(resources, drawableRes, null)?.limitDrawableSize()?.let {
          binding.titleTextView.setCompoundDrawables(null, it, null, null)
        }
      } catch (ignore: NotFoundException) {
        // drawable resource not found
      }
    }
  }

  fun setHorizontalFrameRatio(ratio: Float) {
    if (ratio > 1f) {
      horizontalFrameRatio = ratio
      calculateFrameAndTitlePos()
    }
  }

  fun setCloseVisibilityAndOnClick(visible: Boolean, action: () -> Unit = {}) {
    binding.closeImageView.visibility = if (visible) VISIBLE else GONE
    binding.closeImageView.setOnClickListener { action() }
    if (visible) {
      binding.closeImageView.setTintAndStateAwareBackground()
      ImageViewCompat.setImageTintList(
        binding.closeImageView,
        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.quickie_ink))
      )
    }
  }

  fun setTorchVisibilityAndOnClick(visible: Boolean, action: (Boolean) -> Unit = {}) {
    binding.torchImageView.visibility = if (visible) VISIBLE else GONE
    binding.torchImageView.setOnClickListener { action(!it.isSelected) }
    if (visible) binding.torchImageView.setTintAndStateAwareBackground()
  }

  fun setTorchState(on: Boolean) {
    binding.torchImageView.isSelected = on
    binding.torchImageView.contentDescription = context.getString(
      if (on) R.string.quickie_torch_off else R.string.quickie_torch
    )
    ImageViewCompat.setImageTintList(
      binding.torchImageView,
      ColorStateList.valueOf(ContextCompat.getColor(context, if (on) R.color.quickie_white else R.color.quickie_ink))
    )
  }

  fun setGalleryVisibilityAndOnClick(visible: Boolean, action: () -> Unit) {
    binding.galleryButton.visibility = if (visible) VISIBLE else GONE
    binding.galleryButton.setOnClickListener { action() }
  }

  fun setZoomActions(
    maximum: Float,
    onRatio: (Float) -> Unit,
    onScale: (Float) -> Unit,
    onAutomatic: () -> Unit
  ) {
    zoomControls.bind(maximum, onRatio, onAutomatic)
    gestures.onScale = onScale.takeIf { maximum > 1f }
    if (maximum > 1f) contentDescription = context.getString(R.string.quickie_preview_gestures)
  }

  fun setZoomState(ratio: Float, manual: Boolean) = zoomControls.update(ratio, manual)

  val isZoomInteracting: Boolean get() = zoomControls.isInteracting

  private fun calculateFrameAndTitlePos() {
    val centralX = width / 2
    val contentBottom = height - paddingBottom - binding.bottomControls.height - 24f.toPx()
    val centralY = (paddingTop + contentBottom) / 2
    val minLength = min((width - paddingLeft - paddingRight) / 2f, (contentBottom - paddingTop) / 2f)
    val marginRatio = if (horizontalFrameRatio > 1f) {
      FRAME_MARGIN_RATIO * ((1f / horizontalFrameRatio) * 1.5f)
    } else {
      FRAME_MARGIN_RATIO
    }
    val strokeLength = minLength - (minLength * marginRatio)
    val strokeWidth = STROKE_WIDTH.toPx()
    outerFrame.set(
      centralX - strokeLength,
      centralY - strokeLength / horizontalFrameRatio,
      centralX + strokeLength,
      centralY + strokeLength / horizontalFrameRatio
    )
    innerFrame.set(
      outerFrame.left + strokeWidth,
      outerFrame.top + strokeWidth,
      outerFrame.right - strokeWidth,
      outerFrame.bottom - strokeWidth
    )

    val topInsetsToOuterFrame = (-paddingTop + centralY - strokeLength).roundToInt()
    val titleCenter = (topInsetsToOuterFrame - binding.titleTextView.height) / 2
    binding.titleTextView.updateTopMargin(titleCenter)
    // hide title text if not enough vertical space
    binding.titleTextView.visibility =
      if (topInsetsToOuterFrame < binding.titleTextView.height) INVISIBLE else VISIBLE
  }

  private fun getAccentColor(): Int {
    return TypedValue().let {
      if (context.theme.resolveAttribute(android.R.attr.colorAccent, it, true)) {
        it.data
      } else {
        ContextCompat.getColor(context, R.color.quickie_accent_fallback)
      }
    }
  }

  private fun View.updateTopMargin(topPx: Int) {
    val params = layoutParams as MarginLayoutParams
    if (params.topMargin == topPx) return
    params.topMargin = topPx
    layoutParams = params
  }

  private fun Drawable.limitDrawableSize(): Drawable {
    val heightLimit = ICON_MAX_HEIGHT.toPx()
    val scale = heightLimit / minimumHeight
    if (scale < 1) {
      setBounds(0, 0, (minimumWidth * scale).roundToInt(), (minimumHeight * scale).roundToInt())
    } else {
      setBounds(0, 0, minimumWidth, minimumHeight)
    }
    return this
  }

  private fun View.setTintAndStateAwareBackground() {
    background?.let { drawable ->
      val wrappedDrawable = DrawableCompat.wrap(drawable)

      val states = arrayOf(
        intArrayOf(android.R.attr.state_pressed, android.R.attr.state_selected),
        intArrayOf(android.R.attr.state_pressed, -android.R.attr.state_selected),
        intArrayOf(-android.R.attr.state_pressed, android.R.attr.state_selected),
        intArrayOf()
      )
      val surfaceColor = ContextCompat.getColor(context, R.color.quickie_surface)
      val stateColors = intArrayOf(accentColor, grayColor, accentColor, surfaceColor)
      val colorStateList = ColorStateList(states, stateColors)

      DrawableCompat.setTintList(wrappedDrawable, colorStateList)
      background = wrappedDrawable
    }
  }

  private fun Float.toPx() =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

  companion object {
    private const val BACKGROUND_ALPHA = 0.77 * 255
    private const val STROKE_WIDTH = 4f
    private const val OUT_RADIUS = 16f
    private const val FRAME_MARGIN_RATIO = 1f / 4
    private const val ICON_MAX_HEIGHT = 56f
  }
}