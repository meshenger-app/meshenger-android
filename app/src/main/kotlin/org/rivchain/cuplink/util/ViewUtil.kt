package org.rivchain.cuplink.util

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.ViewStub
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import com.google.android.material.textfield.TextInputEditText
import org.rivchain.cuplink.util.view.Stub


object ViewUtil {
    fun setMinimumHeight(view: View, @Px minimumHeight: Int) {
        if (view.minimumHeight != minimumHeight) {
            view.setMinimumHeight(minimumHeight)
        }
    }

    fun focusAndMoveCursorToEndAndOpenKeyboard(input: TextInputEditText) {
        val numberLength = input.getText()!!.length
        input.setSelection(numberLength, numberLength)
        focusAndShowKeyboard(input)
    }

    fun focusAndShowKeyboard(view: View) {
        view.requestFocus()
        if (view.hasWindowFocus()) {
            showTheKeyboardNow(view)
        } else {
            view.getViewTreeObserver()
                .addOnWindowFocusChangeListener(object : OnWindowFocusChangeListener {
                    override fun onWindowFocusChanged(hasFocus: Boolean) {
                        if (hasFocus) {
                            showTheKeyboardNow(view)
                            view.getViewTreeObserver().removeOnWindowFocusChangeListener(this)
                        }
                    }
                })
        }
    }

    private fun showTheKeyboardNow(view: View) {
        if (view.isFocused) {
            view.post {
                val inputMethodManager: InputMethodManager =
                    ServiceUtil.getInputMethodManager(view.context)
                inputMethodManager.showSoftInput(
                    view,
                    InputMethodManager.SHOW_IMPLICIT
                )
            }
        }
    }

    fun <T : View?> inflateStub(parent: View, @IdRes stubId: Int): T {
        return (parent.findViewById<View>(stubId) as ViewStub).inflate() as T
    }

    fun <T : View?> findStubById(parent: Activity, @IdRes resId: Int): Stub<T> {
        return Stub(parent.findViewById(resId))
    }

    fun <T : View?> findStubById(parent: View, @IdRes resId: Int): Stub<T> {
        return Stub(parent.findViewById(resId))
    }

    private fun getAlphaAnimation(from: Float, to: Float, duration: Int): Animation {
        val anim: Animation = AlphaAnimation(from, to)
        anim.interpolator = FastOutSlowInInterpolator()
        anim.setDuration(duration.toLong())
        return anim
    }

    fun fadeIn(view: View, duration: Int) {
        animateIn(view, getAlphaAnimation(0f, 1f, duration))
    }

    fun fadeOut(view: View, duration: Int): ListenableFuture<Boolean> {
        return fadeOut(view, duration, View.GONE)
    }

    fun fadeOut(view: View, duration: Int, visibility: Int): ListenableFuture<Boolean> {
        return animateOut(view, getAlphaAnimation(1f, 0f, duration), visibility)
    }

    fun animateOut(view: View, animation: Animation): ListenableFuture<Boolean> {
        return animateOut(view, animation, View.GONE)
    }

    fun animateOut(view: View, animation: Animation, visibility: Int): ListenableFuture<Boolean> {
        val future = SettableFuture<Boolean>()
        if (view.visibility == visibility) {
            future.set(true)
        } else {
            view.clearAnimation()
            animation.reset()
            animation.setStartTime(0)
            animation.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation) {}
                override fun onAnimationRepeat(animation: Animation) {}
                override fun onAnimationEnd(animation: Animation) {
                    view.visibility = visibility
                    future.set(true)
                }
            })
            view.startAnimation(animation)
        }
        return future
    }

    fun animateIn(view: View, animation: Animation) {
        if (view.visibility == View.VISIBLE) return
        view.clearAnimation()
        animation.reset()
        animation.setStartTime(0)
        view.visibility = View.VISIBLE
        view.startAnimation(animation)
    }

    fun <T : View?> inflate(
        inflater: LayoutInflater,
        parent: ViewGroup,
        @LayoutRes layoutResId: Int,
    ): T {
        return inflater.inflate(layoutResId, parent, false) as T
    }

    @SuppressLint("RtlHardcoded")
    fun setTextViewGravityStart(textView: TextView, context: Context) {
        if (isRtl(context)) {
            textView.setGravity(Gravity.RIGHT)
        } else {
            textView.setGravity(Gravity.LEFT)
        }
    }

    fun mirrorIfRtl(view: View, context: Context) {
        if (isRtl(context)) {
            view.scaleX = -1.0f
        }
    }

    fun isLtr(view: View): Boolean {
        return isLtr(view.context)
    }

    fun isLtr(context: Context): Boolean {
        return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR
    }

    fun isRtl(view: View): Boolean {
        return isRtl(view.context)
    }

    fun isRtl(context: Context): Boolean {
        return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    fun pxToDp(px: Float): Float {
        return px / Resources.getSystem().displayMetrics.density
    }

    fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density + 0.5).toInt()
    }

    fun dpToPx(dp: Int): Int {
        return Math.round(dp * Resources.getSystem().displayMetrics.density)
    }

    fun dpToSp(dp: Int): Int {
        return (dpToPx(dp) / Resources.getSystem().displayMetrics.scaledDensity).toInt()
    }

    fun spToPx(sp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            sp,
            Resources.getSystem().displayMetrics
        ).toInt()
    }

    fun updateLayoutParams(view: View, width: Int, height: Int) {
        view.layoutParams.width = width
        view.layoutParams.height = height
        view.requestLayout()
    }

    fun updateLayoutParamsIfNonNull(view: View?, width: Int, height: Int) {
        if (view != null) {
            updateLayoutParams(view, width, height)
        }
    }

    fun setVisibilityIfNonNull(view: View?, visibility: Int) {
        if (view != null) {
            view.visibility = visibility
        }
    }

    fun getLeftMargin(view: View): Int {
        return if (isLtr(view)) {
            (view.layoutParams as MarginLayoutParams).leftMargin
        } else (view.layoutParams as MarginLayoutParams).rightMargin
    }

    fun getRightMargin(view: View): Int {
        return if (isLtr(view)) {
            (view.layoutParams as MarginLayoutParams).rightMargin
        } else (view.layoutParams as MarginLayoutParams).leftMargin
    }

    fun getTopMargin(view: View): Int {
        return (view.layoutParams as MarginLayoutParams).topMargin
    }

    fun getBottomMargin(view: View): Int {
        return (view.layoutParams as MarginLayoutParams).bottomMargin
    }

    fun setLeftMargin(view: View, margin: Int) {
        if (isLtr(view)) {
            (view.layoutParams as MarginLayoutParams).leftMargin = margin
        } else {
            (view.layoutParams as MarginLayoutParams).rightMargin = margin
        }
        view.forceLayout()
        view.requestLayout()
    }

    fun setRightMargin(view: View, margin: Int) {
        if (isLtr(view)) {
            (view.layoutParams as MarginLayoutParams).rightMargin = margin
        } else {
            (view.layoutParams as MarginLayoutParams).leftMargin = margin
        }
        view.forceLayout()
        view.requestLayout()
    }

    fun setTopMargin(view: View, @Px margin: Int) {
        setTopMargin(view, margin, true)
    }

    /**
     * Sets the top margin of the view and optionally requests a new layout pass.
     *
     * @param view            The view to set the margin on
     * @param margin          The margin to set
     * @param requestLayout   Whether requestLayout should be invoked on the view
     */
    fun setTopMargin(view: View, @Px margin: Int, requestLayout: Boolean) {
        (view.layoutParams as MarginLayoutParams).topMargin = margin
        if (requestLayout) {
            view.requestLayout()
        }
    }

    fun setBottomMargin(view: View, @Px margin: Int) {
        setBottomMargin(view, margin, true)
    }

    /**
     * Sets the bottom margin of the view and optionally requests a new layout pass.
     *
     * @param view            The view to set the margin on
     * @param margin          The margin to set
     * @param requestLayout   Whether requestLayout should be invoked on the view
     */
    fun setBottomMargin(view: View, @Px margin: Int, requestLayout: Boolean) {
        (view.layoutParams as MarginLayoutParams).bottomMargin = margin
        if (requestLayout) {
            view.requestLayout()
        }
    }

    fun getWidth(view: View): Int {
        return view.layoutParams.width
    }

    fun setPaddingTop(view: View, padding: Int) {
        view.setPadding(view.getPaddingLeft(), padding, view.getPaddingRight(), view.paddingBottom)
    }

    fun setPaddingBottom(view: View, padding: Int) {
        view.setPadding(view.getPaddingLeft(), view.paddingTop, view.getPaddingRight(), padding)
    }

    fun setPadding(view: View, padding: Int) {
        view.setPadding(padding, padding, padding, padding)
    }

    fun setPaddingStart(view: View, padding: Int) {
        if (isLtr(view)) {
            view.setPadding(padding, view.paddingTop, view.getPaddingRight(), view.paddingBottom)
        } else {
            view.setPadding(view.getPaddingLeft(), view.paddingTop, padding, view.paddingBottom)
        }
    }

    fun setPaddingEnd(view: View, padding: Int) {
        if (isLtr(view)) {
            view.setPadding(view.getPaddingLeft(), view.paddingTop, padding, view.paddingBottom)
        } else {
            view.setPadding(padding, view.paddingTop, view.getPaddingRight(), view.paddingBottom)
        }
    }

    fun isPointInsideView(view: View, x: Float, y: Float): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]
        return x > viewX && x < viewX + view.width && y > viewY && y < viewY + view.height
    }

    fun getStatusBarHeight(view: View): Int {
        val rootWindowInsets = ViewCompat.getRootWindowInsets(view)
        return if (Build.VERSION.SDK_INT > 29 && rootWindowInsets != null) {
            rootWindowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        } else {
            var result = 0
            val resourceId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resourceId > 0) {
                result = view.resources.getDimensionPixelSize(resourceId)
            }
            result
        }
    }

    fun getNavigationBarHeight(view: View): Int {
        val rootWindowInsets = ViewCompat.getRootWindowInsets(view)
        return if (Build.VERSION.SDK_INT > 29 && rootWindowInsets != null) {
            rootWindowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        } else {
            var result = 0
            val resourceId =
                view.resources.getIdentifier("navigation_bar_height", "dimen", "android")
            if (resourceId > 0) {
                result = view.resources.getDimensionPixelSize(resourceId)
            }
            result
        }
    }

    fun hideKeyboard(context: Context, view: View) {
        val inputManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    /**
     * Enables or disables a view and all child views recursively.
     */
    fun setEnabledRecursive(view: View, enabled: Boolean) {
        view.setEnabled(enabled)
        if (view is ViewGroup) {
            val viewGroup = view
            for (i in 0 until viewGroup.childCount) {
                setEnabledRecursive(viewGroup.getChildAt(i), enabled)
            }
        }
    }

    fun getActivityLifecycle(view: View): Lifecycle? {
        return getActivityLifecycle(view.context)
    }

    private fun getActivityLifecycle(context: Context?): Lifecycle? {
        if (context is ContextThemeWrapper) {
            return getActivityLifecycle(context.baseContext)
        }
        return if (context is AppCompatActivity) {
            context.lifecycle
        } else null
    }

    fun supportsPictureInPicture(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_PICTURE_IN_PICTURE
        )
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun createPictureInPictureParams(context: Context): PictureInPictureParams? {
        val aspectRatio =
            if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Rational(16, 9)
            } else {
                Rational(9, 16)
            }

        val pipParamsBuilder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pipParamsBuilder.setAutoEnterEnabled(true)
        }

        return pipParamsBuilder.build()
    }


    fun setPictureInPicture(context: Context): PictureInPictureParams? {
        if (supportsPictureInPicture(context)) {
            val params = createPictureInPictureParams(context)
            if (params != null) {
                return params
            } else {
                Log.i(this,"PictureInPictureParams are null")
            }
        }
        return null
    }
}