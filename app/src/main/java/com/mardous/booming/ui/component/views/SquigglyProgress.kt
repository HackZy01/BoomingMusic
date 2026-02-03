package com.mardous.booming.ui.component.views

/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.mardous.booming.extensions.resources.withAlpha

private const val TWO_PI = (Math.PI * 2f).toFloat()

@VisibleForTesting
internal const val DISABLED_ALPHA = 77

class SquigglyProgress : Drawable() {

    private val wavePaint = Paint()
    private val linePaint = Paint()
    private val path = Path()
    private var heightFraction = 1f
    private var heightAnimator: ValueAnimator? = null
    private var phaseOffset = 0f
    private var lastFrameTime = -1L

    /* distance over which amplitude drops to zero, measured in wavelengths */
    private val transitionPeriods = 1.5f

    /* wave endpoint as percentage of bar when play position is zero */
    private val minWaveEndpoint = 0f

    /* wave endpoint as percentage of bar when play position matches wave endpoint */
    private val matchedWaveEndpoint = 0f

    // Horizontal length of the sine wave
    var waveLength = 55f

    // Height of each peak of the sine wave
    var lineAmplitude = 6f

    // Line speed in px per second
    var phaseSpeed = 16f

    // Progress stroke width, both for wave and solid line
    var strokeWidth = 8f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            wavePaint.strokeWidth = value
            linePaint.strokeWidth = value
        }

    // Enables a transition region where the amplitude
    // of the wave is reduced linearly across it.
    var transitionEnabled = true
        set(value) {
            field = value
            invalidateSelf()
        }

    init {
        wavePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeCap = Paint.Cap.ROUND
        wavePaint.strokeWidth = strokeWidth
        linePaint.strokeWidth = strokeWidth
        linePaint.style = Paint.Style.STROKE
        wavePaint.style = Paint.Style.STROKE
        linePaint.alpha = DISABLED_ALPHA
    }

    var animate: Boolean = true
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (field) {
                lastFrameTime = SystemClock.uptimeMillis()
            }
            heightAnimator?.cancel()
            heightAnimator =
                ValueAnimator.ofFloat(heightFraction, if (animate) 1f else 0f).apply {
                    if (animate) {
                        startDelay = 60
                        duration = 800
//                        interpolator = Interpolators.EMPHASIZED_DECELERATE
                    } else {
                        duration = 550
//                        interpolator = Interpolators.STANDARD_DECELERATE
                    }
                    addUpdateListener {
                        heightFraction = it.animatedValue as Float
                        invalidateSelf()
                    }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                heightAnimator = null
                            }
                        }
                    )
                    start()
                }
        }

    override fun draw(canvas: Canvas) {
        if (animate) {
            invalidateSelf()
            val now = SystemClock.uptimeMillis()
            phaseOffset += (now - lastFrameTime) / 1000f * phaseSpeed
            phaseOffset %= waveLength
            lastFrameTime = now
        }

        val progress = level / 10_000f
        var totalWidth = bounds.width().toFloat()

        if (transitionEnabled) {
            totalWidth -= transitionPeriods * waveLength
        }
        val totalProgressPx = bounds.width().toFloat() * progress
        val waveProgressPx =
            totalWidth *
                    (if (!transitionEnabled || progress > matchedWaveEndpoint) progress
                    else
                        lerp(
                            minWaveEndpoint,
                            matchedWaveEndpoint,
                            lerpInv(0f, matchedWaveEndpoint, progress)
                        ))

        // Build Wiggly Path
        val waveStart = -phaseOffset - waveLength / 2f
        val waveEnd = if (transitionEnabled) bounds.width().toFloat() else waveProgressPx

        // helper function, computes amplitude for wave segment
        val computeAmplitude: (Float, Float) -> Float = { x, sign ->
            if (transitionEnabled) {
                val length = transitionPeriods * waveLength
                val coeff =
                    lerpInvSat(waveProgressPx + length / 2f, waveProgressPx - length / 2f, x)
                sign * heightFraction * lineAmplitude * coeff
            } else {
                sign * heightFraction * lineAmplitude
            }
        }

        // Reset path object to the start
        path.rewind()
        path.moveTo(waveStart, 0f)

        // Build the wave, incrementing by half the wavelength each time
        var currentX = waveStart
        var waveSign = 1f // Fixed: was 1.2f, which distorted amplitude
        var currentAmp = computeAmplitude(currentX, waveSign)
        val dist = waveLength / 2f
        
        // Control point offset for a smooth sine wave.
        // Approx 0.36 * distance gives a good sine approximation.
        val controlDist = dist * 0.36f

        while (currentX < waveEnd) {
            waveSign = -waveSign
            val nextX = currentX + dist
            val nextAmp = computeAmplitude(nextX, waveSign)
            
            // Fixed: Use distributed control points for a smooth sine shape
            // instead of stacking them in the middle (midX).
            path.cubicTo(
                currentX + controlDist, currentAmp,
                nextX - controlDist, nextAmp,
                nextX, nextAmp
            )
            
            currentAmp = nextAmp
            currentX = nextX
        }

        // translate to the start position of the progress bar for all draw commands
        val clipTop = lineAmplitude + strokeWidth
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.centerY().toFloat())

        // Draw path up to progress position
        canvas.save()
        canvas.clipRect(0f, -1f * clipTop, totalProgressPx, clipTop)
        canvas.drawPath(path, wavePaint)
        canvas.restore()

        if (transitionEnabled) {
            // If there's a smooth transition, we draw the rest of the
            // path in a different color (using different clip params)
            canvas.save()
            canvas.clipRect(totalProgressPx, -1f * clipTop, bounds.width().toFloat(), clipTop)
            canvas.drawPath(path, linePaint)
            canvas.restore()
        } else {
            // No transition, just draw a flat line to the end of the region.
            // The discontinuity is hidden by the progress bar thumb shape.
            canvas.drawLine(totalProgressPx, 0f, totalWidth, 0f, linePaint)
        }

        canvas.restore()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        wavePaint.colorFilter = colorFilter
        linePaint.colorFilter = colorFilter
    }

    override fun setAlpha(alpha: Int) {
        updateColors(wavePaint.color, alpha)
    }

    override fun getAlpha(): Int {
        return wavePaint.alpha
    }

    override fun setTint(tintColor: Int) {
        updateColors(tintColor, alpha)
    }

    override fun onLevelChange(level: Int): Boolean {
        return animate
    }

    override fun setTintList(tint: ColorStateList?) {
        if (tint == null) {
            return
        }
        updateColors(tint.defaultColor, alpha)
    }

    private fun updateColors(tintColor: Int, alpha: Int) {
        wavePaint.color = tintColor.withAlpha(alpha / 255f)
        linePaint.color = tintColor.withAlpha((DISABLED_ALPHA * (alpha / 255f)) / 255f)
    }

    fun constrain(amount: Float, low: Float, high: Float): Float {
        return if (amount < low) low else if (amount > high) high else amount
    }

    private fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

    /**
     * Returns the interpolation scalar (s) that satisfies the equation: `value = `[ ][.lerp]`(a, b, s)`
     *
     *
     * If `a == b`, then this function will return 0.
     */
    fun lerpInv(a: Float, b: Float, value: Float): Float {
        return if (a != b) (value - a) / (b - a) else 0.0f
    }

    /** Returns the single argument constrained between [0.0, 1.0].
     */
    fun saturate(value: Float): Float {
        return constrain(value, 0.0f, 1.0f)
    }

    /** Returns the saturated (constrained between [0, 1]) result of [.lerpInv].
     */
    fun lerpInvSat(a: Float, b: Float, value: Float): Float {
        return saturate(lerpInv(a, b, value))
    }
}
        set(value) {
            [span_16](start_span)if (field == value) return[span_16](end_span)
            field = value
            [span_17](start_span)wavePaint.strokeWidth = value[span_17](end_span)
            [span_18](start_span)linePaint.strokeWidth = value[span_18](end_span)
        }

    [span_19](start_span)var transitionEnabled = true[span_19](end_span)
        set(value) {
            field = value
            [span_20](start_span)invalidateSelf()[span_20](end_span)
        }

    init {
        [span_21](start_span)wavePaint.strokeCap = Paint.Cap.ROUND[span_21](end_span)
        [span_22](start_span)linePaint.strokeCap = Paint.Cap.ROUND[span_22](end_span)
        [span_23](start_span)linePaint.style = Paint.Style.STROKE[span_23](end_span)
        [span_24](start_span)wavePaint.style = Paint.Style.STROKE[span_24](end_span)
        [span_25](start_span)linePaint.alpha = DISABLED_ALPHA[span_25](end_span)
    }

    [span_26](start_span)var animate: Boolean = true[span_26](end_span)
        set(value) {
            [span_27](start_span)if (field == value) return[span_27](end_span)
            [span_28](start_span)field = value[span_28](end_span)
            if (field) {
                [span_29](start_span)lastFrameTime = SystemClock.uptimeMillis()[span_29](end_span)
            }
            [span_30](start_span)heightAnimator?.cancel()[span_30](end_span)
            heightAnimator = ValueAnimator.ofFloat(heightFraction, if (animate) 1f else 0f).apply {
                [span_31](start_span)duration = if (animate) 800 else 550[span_31](end_span)
                [span_32](start_span)startDelay = if (animate) 60 else 0[span_32](end_span)
                addUpdateListener {
                    [span_33](start_span)heightFraction = it.animatedValue as Float[span_33](end_span)
                    [span_34](start_span)invalidateSelf()[span_34](end_span)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        [span_35](start_span)heightAnimator = null[span_35](end_span)
                    }
                })
                [span_36](start_span)start()[span_36](end_span)
            }
        }

    override fun draw(canvas: Canvas) {
        if (animate) {
            [span_37](start_span)invalidateSelf()[span_37](end_span)
            [span_38](start_span)val now = SystemClock.uptimeMillis()[span_38](end_span)
            [span_39](start_span)phaseOffset += (now - lastFrameTime) / 1000f * phaseSpeed[span_39](end_span)
            [span_40](start_span)phaseOffset %= waveLength[span_40](end_span)
            [span_41](start_span)lastFrameTime = now[span_41](end_span)
        }

        [span_42](start_span)val progress = level / 10_000f[span_42](end_span)
        [span_43](start_span)val totalWidth = bounds.width().toFloat()[span_43](end_span)
        [span_44](start_span)val totalProgressPx = totalWidth * progress[span_44](end_span)
        val waveProgressPx = totalWidth * (if (!transitionEnabled || progress > matchedWaveEndpoint) progress 
            [span_45](start_span)else lerp(minWaveEndpoint, matchedWaveEndpoint, lerpInv(0f, matchedWaveEndpoint, progress)))[span_45](end_span)

        [span_46](start_span)val waveStart = -phaseOffset - waveLength / 2f[span_46](end_span)
        [span_47](start_span)val waveEnd = if (transitionEnabled) totalWidth else waveProgressPx[span_47](end_span)

        val computeAmplitude: (Float, Float) -> Float = { x, sign ->
            if (transitionEnabled) {
                [span_48](start_span)val length = transitionPeriods * waveLength[span_48](end_span)
                [span_49](start_span)val coeff = lerpInvSat(waveProgressPx + length / 2f, waveProgressPx - length / 2f, x)[span_49](end_span)
                [span_50](start_span)sign * heightFraction * lineAmplitude * coeff[span_50](end_span)
            } else {
                [span_51](start_span)sign * heightFraction * lineAmplitude[span_51](end_span)
            }
        }

        [span_52](start_span)path.rewind()[span_52](end_span)
        [span_53](start_span)path.moveTo(waveStart, 0f)[span_53](end_span)

        [span_54](start_span)var currentX = waveStart[span_54](end_span)
        var waveSign = 1f 
        [span_55](start_span)var currentAmp = computeAmplitude(currentX, waveSign)[span_55](end_span)
        [span_56](start_span)val dist = waveLength / 2f[span_56](end_span)

        while (currentX < waveEnd) {
            [span_57](start_span)waveSign = -waveSign[span_57](end_span)
            [span_58](start_span)val nextX = currentX + dist[span_58](end_span)
            [span_59](start_span)val midX = currentX + dist / 2[span_59](end_span)
            [span_60](start_span)val nextAmp = computeAmplitude(nextX, waveSign)[span_60](end_span)
            [span_61](start_span)path.cubicTo(midX, currentAmp, midX, nextAmp, nextX, nextAmp)[span_61](end_span)
            [span_62](start_span)currentAmp = nextAmp[span_62](end_span)
            [span_63](start_span)currentX = nextX[span_63](end_span)
        }

        [span_64](start_span)val clipTop = lineAmplitude + strokeWidth[span_64](end_span)
        [span_65](start_span)canvas.save()[span_65](end_span)
        [span_66](start_span)canvas.translate(bounds.left.toFloat(), bounds.centerY().toFloat())[span_66](end_span)

        [span_67](start_span)canvas.save()[span_67](end_span)
        [span_68](start_span)canvas.clipRect(0f, -1f * clipTop, totalProgressPx, clipTop)[span_68](end_span)
        [span_69](start_span)canvas.drawPath(path, wavePaint)[span_69](end_span)
        [span_70](start_span)canvas.restore()[span_70](end_span)

        if (transitionEnabled) {
            [span_71](start_span)canvas.save()[span_71](end_span)
            [span_72](start_span)canvas.clipRect(totalProgressPx, -1f * clipTop, totalWidth, clipTop)[span_72](end_span)
            [span_73](start_span)canvas.drawPath(path, linePaint)[span_73](end_span)
            [span_74](start_span)canvas.restore()[span_74](end_span)
        } else {
            [span_75](start_span)canvas.drawLine(totalProgressPx, 0f, totalWidth, 0f, linePaint)[span_75](end_span)
        }

        // Draw round line cap at the beginning (from Squiggly.kt)
        val startAmp = cos(abs(waveStart) / waveLength * TWO_PI)
        canvas.drawPoint(0f, startAmp * lineAmplitude * heightFraction, wavePaint)

        [span_76](start_span)canvas.restore()[span_76](end_span)
    }

    [span_77](start_span)// Helper Math Functions[span_77](end_span)
    private fun lerp(start: Float, stop: Float, amount: Float): Float = start + (stop - start) * amount
    private fun lerpInv(a: Float, b: Float, value: Float): Float = if (a != b) (value - a) / (b - a) else 0.0f
    private fun saturate(value: Float): Float = if (value < 0f) 0f else if (value > 1f) 1f else value
    private fun lerpInvSat(a: Float, b: Float, value: Float): Float = saturate(lerpInv(a, b, value))

    [span_78](start_span)override fun getOpacity(): Int = PixelFormat.TRANSLUCENT[span_78](end_span)
    override fun setColorFilter(colorFilter: ColorFilter?) {
        [span_79](start_span)wavePaint.colorFilter = colorFilter[span_79](end_span)
        [span_80](start_span)linePaint.colorFilter = colorFilter[span_80](end_span)
    }
    [span_81](start_span)override fun setAlpha(alpha: Int) = updateColors(wavePaint.color, alpha)[span_81](end_span)
    [span_82](start_span)override fun getAlpha(): Int = wavePaint.alpha[span_82](end_span)
    [span_83](start_span)override fun setTint(tintColor: Int) = updateColors(tintColor, alpha)[span_83](end_span)
    [span_84](start_span)override fun onLevelChange(level: Int): Boolean = animate[span_84](end_span)
    
    override fun setTintList(tint: ColorStateList?) {
        [span_85](start_span)if (tint != null) updateColors(tint.defaultColor, alpha)[span_85](end_span)
    }

    private fun updateColors(tintColor: Int, alpha: Int) {
        [span_86](start_span)wavePaint.color = tintColor.withAlpha(alpha / 255f)[span_86](end_span)
        [span_87](start_span)linePaint.color = tintColor.withAlpha((DISABLED_ALPHA * (alpha / 255f)) / 255f)[span_87](end_span)
    }
}
    private val linePaint = Paint()
    private val path = Path()
    private var heightFraction = 1f
    private var heightAnimator: ValueAnimator? = null
    private var phaseOffset = 0f
    private var lastFrameTime = -1L

    /* distance over which amplitude drops to zero, measured in wavelengths */
    private val transitionPeriods = 1.5f

    /* wave endpoint as percentage of bar when play position is zero */
    private val minWaveEndpoint = 0f

    /* wave endpoint as percentage of bar when play position matches wave endpoint */
    private val matchedWaveEndpoint = 0f

    // Horizontal length of the sine wave
    var waveLength = 55f

    // Height of each peak of the sine wave
    var lineAmplitude = 6f

    // Line speed in px per second
    var phaseSpeed = 16f

    // Progress stroke width, both for wave and solid line
    var strokeWidth = 8f
        set(value) {
            if (field == value) {
                return
            }
            field = value
            wavePaint.strokeWidth = value
            linePaint.strokeWidth = value
        }

    // Enables a transition region where the amplitude
    // of the wave is reduced linearly across it.
    var transitionEnabled = true
        set(value) {
            field = value
            invalidateSelf()
        }

    init {
        wavePaint.strokeCap = Paint.Cap.ROUND
        linePaint.strokeCap = Paint.Cap.ROUND
        wavePaint.strokeWidth = strokeWidth
        linePaint.strokeWidth = strokeWidth
        linePaint.style = Paint.Style.STROKE
        wavePaint.style = Paint.Style.STROKE
        linePaint.alpha = DISABLED_ALPHA
    }

    var animate: Boolean = true
        set(value) {
            if (field == value) {
                return
            }
            field = value
            if (field) {
                lastFrameTime = SystemClock.uptimeMillis()
            }
            heightAnimator?.cancel()
            heightAnimator =
                ValueAnimator.ofFloat(heightFraction, if (animate) 1f else 0f).apply {
                    if (animate) {
                        startDelay = 60
                        duration = 800
//                        interpolator = Interpolators.EMPHASIZED_DECELERATE
                    } else {
                        duration = 550
//                        interpolator = Interpolators.STANDARD_DECELERATE
                    }
                    addUpdateListener {
                        heightFraction = it.animatedValue as Float
                        invalidateSelf()
                    }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                heightAnimator = null
                            }
                        }
                    )
                    start()
                }
        }

    override fun draw(canvas: Canvas) {
        if (animate) {
            invalidateSelf()
            val now = SystemClock.uptimeMillis()
            phaseOffset += (now - lastFrameTime) / 1000f * phaseSpeed
            phaseOffset %= waveLength
            lastFrameTime = now
        }

        val progress = level / 10_000f
        var totalWidth = bounds.width().toFloat()

        if (transitionEnabled) {
            totalWidth -= transitionPeriods * waveLength
        }
        val totalProgressPx = bounds.width().toFloat() * progress
        val waveProgressPx =
            totalWidth *
                    (if (!transitionEnabled || progress > matchedWaveEndpoint) progress
                    else
                        lerp(
                            minWaveEndpoint,
                            matchedWaveEndpoint,
                            lerpInv(0f, matchedWaveEndpoint, progress)
                        ))

        // Build Wiggly Path
        val waveStart = -phaseOffset - waveLength / 2f
        val waveEnd = if (transitionEnabled) bounds.width().toFloat() else waveProgressPx

        // helper function, computes amplitude for wave segment
        val computeAmplitude: (Float, Float) -> Float = { x, sign ->
            if (transitionEnabled) {
                val length = transitionPeriods * waveLength
                val coeff =
                    lerpInvSat(waveProgressPx + length / 2f, waveProgressPx - length / 2f, x)
                sign * heightFraction * lineAmplitude * coeff
            } else {
                sign * heightFraction * lineAmplitude
            }
        }

        // Reset path object to the start
        path.rewind()
        path.moveTo(waveStart, 0f)

        // Build the wave, incrementing by half the wavelength each time
        var currentX = waveStart
        var waveSign = 1.2f
        var currentAmp = computeAmplitude(currentX, waveSign)
        val dist = waveLength / 2f
        while (currentX < waveEnd) {
            waveSign = -waveSign
            val nextX = currentX + dist
            val midX = currentX + dist / 2
            val nextAmp = computeAmplitude(nextX, waveSign)
            path.cubicTo(midX, currentAmp, midX, nextAmp, nextX, nextAmp)
            currentAmp = nextAmp
            currentX = nextX
        }

        // translate to the start position of the progress bar for all draw commands
        val clipTop = lineAmplitude + strokeWidth
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.centerY().toFloat())

        // Draw path up to progress position
        canvas.save()
        canvas.clipRect(0f, -1f * clipTop, totalProgressPx, clipTop)
        canvas.drawPath(path, wavePaint)
        canvas.restore()

        if (transitionEnabled) {
            // If there's a smooth transition, we draw the rest of the
            // path in a different color (using different clip params)
            canvas.save()
            canvas.clipRect(totalProgressPx, -1f * clipTop, bounds.width().toFloat(), clipTop)
            canvas.drawPath(path, linePaint)
            canvas.restore()
        } else {
            // No transition, just draw a flat line to the end of the region.
            // The discontinuity is hidden by the progress bar thumb shape.
            canvas.drawLine(totalProgressPx, 0f, totalWidth, 0f, linePaint)
        }

        canvas.restore()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        wavePaint.colorFilter = colorFilter
        linePaint.colorFilter = colorFilter
    }

    override fun setAlpha(alpha: Int) {
        updateColors(wavePaint.color, alpha)
    }

    override fun getAlpha(): Int {
        return wavePaint.alpha
    }

    override fun setTint(tintColor: Int) {
        updateColors(tintColor, alpha)
    }

    override fun onLevelChange(level: Int): Boolean {
        return animate
    }

    override fun setTintList(tint: ColorStateList?) {
        if (tint == null) {
            return
        }
        updateColors(tint.defaultColor, alpha)
    }

    private fun updateColors(tintColor: Int, alpha: Int) {
        wavePaint.color = tintColor.withAlpha(alpha / 255f)
        linePaint.color = tintColor.withAlpha((DISABLED_ALPHA * (alpha / 255f)) / 255f)
    }

    fun constrain(amount: Float, low: Float, high: Float): Float {
        return if (amount < low) low else if (amount > high) high else amount
    }

    private fun lerp(start: Float, stop: Float, amount: Float): Float {
        return start + (stop - start) * amount
    }

    /**
     * Returns the interpolation scalar (s) that satisfies the equation: `value = `[ ][.lerp]`(a, b, s)`
     *
     *
     * If `a == b`, then this function will return 0.
     */
    fun lerpInv(a: Float, b: Float, value: Float): Float {
        return if (a != b) (value - a) / (b - a) else 0.0f
    }

    /** Returns the single argument constrained between [0.0, 1.0].  */
    fun saturate(value: Float): Float {
        return constrain(value, 0.0f, 1.0f)
    }

    /** Returns the saturated (constrained between [0, 1]) result of [.lerpInv].  */
    fun lerpInvSat(a: Float, b: Float, value: Float): Float {
        return saturate(lerpInv(a, b, value))
    }
}
