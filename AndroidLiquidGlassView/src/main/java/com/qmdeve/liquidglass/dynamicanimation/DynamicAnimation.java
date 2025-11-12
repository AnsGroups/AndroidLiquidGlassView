/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qmdeve.liquidglass.dynamicanimation;

import android.annotation.SuppressLint;
import android.util.AndroidRuntimeException;
import android.view.View;

import androidx.annotation.FloatRange;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;

public abstract class DynamicAnimation<T extends DynamicAnimation<T>> implements AnimationHandler.AnimationFrameCallback {

    public abstract static class ViewProperty extends FloatPropertyCompat<View> {
        private ViewProperty(String name) {
            super(name);
        }
    }

    public static final ViewProperty SCALE_X = new ViewProperty("scaleX") {
        @Override
        public void setValue(View view, float value) {
            view.setScaleX(value);
        }

        @Override
        public float getValue(View view) {
            return view.getScaleX();
        }
    };

    public static final ViewProperty SCALE_Y = new ViewProperty("scaleY") {
        @Override
        public void setValue(View view, float value) {
            view.setScaleY(value);
        }

        @Override
        public float getValue(View view) {
            return view.getScaleY();
        }
    };

    public static final ViewProperty ROTATION = new ViewProperty("rotation") {
        @Override
        public void setValue(View view, float value) {
            view.setRotation(value);
        }

        @Override
        public float getValue(View view) {
            return view.getRotation();
        }
    };

    public static final ViewProperty ROTATION_X = new ViewProperty("rotationX") {
        @Override
        public void setValue(View view, float value) {
            view.setRotationX(value);
        }

        @Override
        public float getValue(View view) {
            return view.getRotationX();
        }
    };

    public static final ViewProperty ROTATION_Y = new ViewProperty("rotationY") {
        @Override
        public void setValue(View view, float value) {
            view.setRotationY(value);
        }

        @Override
        public float getValue(View view) {
            return view.getRotationY();
        }
    };

    public static final ViewProperty X = new ViewProperty("x") {
        @Override
        public void setValue(View view, float value) {
            view.setX(value);
        }

        @Override
        public float getValue(View view) {
            return view.getX();
        }
    };

    public static final ViewProperty Y = new ViewProperty("y") {
        @Override
        public void setValue(View view, float value) {
            view.setY(value);
        }

        @Override
        public float getValue(View view) {
            return view.getY();
        }
    };

    public static final ViewProperty ALPHA = new ViewProperty("alpha") {
        @Override
        public void setValue(View view, float value) {
            view.setAlpha(value);
        }

        @Override
        public float getValue(View view) {
            return view.getAlpha();
        }
    };

    public static final ViewProperty SCROLL_X = new ViewProperty("scrollX") {
        @Override
        public void setValue(View view, float value) {
            view.setScrollX((int) value);
        }

        @Override
        public float getValue(View view) {
            return view.getScrollX();
        }
    };

    @SuppressLint("MinMaxConstant")
    public static final float MIN_VISIBLE_CHANGE_PIXELS = 1f;
    @SuppressLint("MinMaxConstant")
    public static final float MIN_VISIBLE_CHANGE_ROTATION_DEGREES = 1f / 10f;
    @SuppressLint("MinMaxConstant")
    public static final float MIN_VISIBLE_CHANGE_ALPHA = 1f / 256f;
    @SuppressLint("MinMaxConstant")
    public static final float MIN_VISIBLE_CHANGE_SCALE = 1f / 500f;
    private static final float UNSET = Float.MAX_VALUE;
    private static final float THRESHOLD_MULTIPLIER = 0.75f;
    float mVelocity = 0;
    float mValue = UNSET;
    boolean mStartValueIsSet = false;
    final Object mTarget;
    final FloatPropertyCompat mProperty;
    boolean mRunning = false;
    float mMaxValue = Float.MAX_VALUE;
    float mMinValue = -mMaxValue;
    private long mLastFrameTime = 0;
    private float mMinVisibleChange;
    private final ArrayList<OnAnimationEndListener> mEndListeners = new ArrayList<>();
    private final ArrayList<OnAnimationUpdateListener> mUpdateListeners = new ArrayList<>();
    private AnimationHandler mAnimationHandler;

    static class MassState {
        float mValue;
        float mVelocity;
    }

    DynamicAnimation(final FloatValueHolder floatValueHolder) {
        mTarget = null;
        mProperty = new FloatPropertyCompat("FloatValueHolder") {
            @Override
            public float getValue(Object object) {
                return floatValueHolder.getValue();
            }

            @Override
            public void setValue(Object object, float value) {
                floatValueHolder.setValue(value);
            }
        };
        mMinVisibleChange = MIN_VISIBLE_CHANGE_PIXELS;
    }

    <K> DynamicAnimation(K object, FloatPropertyCompat<K> property) {
        mTarget = object;
        mProperty = property;
        if (mProperty == ROTATION || mProperty == ROTATION_X
                || mProperty == ROTATION_Y) {
            mMinVisibleChange = MIN_VISIBLE_CHANGE_ROTATION_DEGREES;
        } else if (mProperty == ALPHA) {
            mMinVisibleChange = MIN_VISIBLE_CHANGE_ALPHA;
        } else if (mProperty == SCALE_X || mProperty == SCALE_Y) {
            mMinVisibleChange = MIN_VISIBLE_CHANGE_SCALE;
        } else {
            mMinVisibleChange = MIN_VISIBLE_CHANGE_PIXELS;
        }
    }

    @SuppressWarnings("unchecked")
    public T setStartValue(float startValue) {
        mValue = startValue;
        mStartValueIsSet = true;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setStartVelocity(float startVelocity) {
        mVelocity = startVelocity;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setMaxValue(float max) {
        mMaxValue = max;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T setMinValue(float min) {
        mMinValue = min;
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    public T addEndListener(OnAnimationEndListener listener) {
        if (!mEndListeners.contains(listener)) {
            mEndListeners.add(listener);
        }
        return (T) this;
    }

    public void removeEndListener(OnAnimationEndListener listener) {
        removeEntry(mEndListeners, listener);
    }

    @SuppressWarnings("unchecked")
    public T addUpdateListener(OnAnimationUpdateListener listener) {
        if (isRunning()) {
            throw new UnsupportedOperationException("Error: Update listeners must be added before"
                    + "the animation.");
        }
        if (!mUpdateListeners.contains(listener)) {
            mUpdateListeners.add(listener);
        }
        return (T) this;
    }

    public void removeUpdateListener(OnAnimationUpdateListener listener) {
        removeEntry(mUpdateListeners, listener);
    }

    @SuppressWarnings("unchecked")
    public T setMinimumVisibleChange(@FloatRange(from = 0.0, fromInclusive = false)
                                     float minimumVisibleChange) {
        if (minimumVisibleChange <= 0) {
            throw new IllegalArgumentException("Minimum visible change must be positive.");
        }
        mMinVisibleChange = minimumVisibleChange;
        setValueThreshold(minimumVisibleChange * THRESHOLD_MULTIPLIER);
        return (T) this;
    }

    public float getMinimumVisibleChange() {
        return mMinVisibleChange;
    }

    private static <T> void removeNullEntries(ArrayList<T> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i) == null) {
                list.remove(i);
            }
        }
    }

    private static <T> void removeEntry(ArrayList<T> list, T entry) {
        int id = list.indexOf(entry);
        if (id >= 0) {
            list.set(id, null);
        }
    }

    @MainThread
    public void start() {
        if (!getAnimationHandler().isCurrentThread()) {
            throw new AndroidRuntimeException("Animations may only be started on the same thread "
                    + "as the animation handler");
        }
        if (!mRunning) {
            startAnimationInternal();
        }
    }

    @MainThread
    public void cancel() {
        if (!getAnimationHandler().isCurrentThread()) {
            throw new AndroidRuntimeException("Animations may only be canceled from the same "
                    + "thread as the animation handler");
        }
        if (mRunning) {
            endAnimationInternal(true);
        }
    }

    public boolean isRunning() {
        return mRunning;
    }

    private void startAnimationInternal() {
        if (!mRunning) {
            mRunning = true;
            if (!mStartValueIsSet) {
                mValue = getPropertyValue();
            }

            if (mValue > mMaxValue || mValue < mMinValue) {
                throw new IllegalArgumentException("Starting value need to be in between min"
                        + " value and max value");
            }
            getAnimationHandler().addAnimationFrameCallback(this, 0);
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public boolean doAnimationFrame(long frameTime) {
        if (mLastFrameTime == 0) {
            mLastFrameTime = frameTime;
            setPropertyValue(mValue);
            return false;
        }
        long deltaT = frameTime - mLastFrameTime;
        mLastFrameTime = frameTime;
        float durationScale = getAnimationHandler().getDurationScale();
        deltaT = durationScale == 0.0f ? Integer.MAX_VALUE : (long) (deltaT / durationScale);
        boolean finished = updateValueAndVelocity(deltaT);
        mValue = Math.min(mValue, mMaxValue);
        mValue = Math.max(mValue, mMinValue);

        setPropertyValue(mValue);

        if (finished) {
            endAnimationInternal(false);
        }
        return finished;
    }

    abstract boolean updateValueAndVelocity(long deltaT);

    private void endAnimationInternal(boolean canceled) {
        mRunning = false;
        getAnimationHandler().removeCallback(this);
        mLastFrameTime = 0;
        mStartValueIsSet = false;
        for (int i = 0; i < mEndListeners.size(); i++) {
            if (mEndListeners.get(i) != null) {
                mEndListeners.get(i).onAnimationEnd(this, canceled, mValue, mVelocity);
            }
        }
        removeNullEntries(mEndListeners);
    }

    @SuppressWarnings("unchecked")
    void setPropertyValue(float value) {
        mProperty.setValue(mTarget, value);
        for (int i = 0; i < mUpdateListeners.size(); i++) {
            if (mUpdateListeners.get(i) != null) {
                mUpdateListeners.get(i).onAnimationUpdate(this, mValue, mVelocity);
            }
        }
        removeNullEntries(mUpdateListeners);
    }

    float getValueThreshold() {
        return mMinVisibleChange * THRESHOLD_MULTIPLIER;
    }

    @SuppressWarnings("unchecked")
    private float getPropertyValue() {
        return mProperty.getValue(mTarget);
    }

    @VisibleForTesting
    public @NonNull AnimationHandler getAnimationHandler() {
        return mAnimationHandler != null ? mAnimationHandler : AnimationHandler.getInstance();
    }

    public @NonNull FrameCallbackScheduler getScheduler() {
        return mAnimationHandler != null ? mAnimationHandler.getScheduler() : AnimationHandler.getInstance().getScheduler();
    }

    public void setScheduler(@NonNull FrameCallbackScheduler scheduler) {
        if (mAnimationHandler != null && mAnimationHandler.getScheduler() == scheduler) {
            return;
        }
        if (mRunning) {
            throw new AndroidRuntimeException("Animations are still running and the animation"
                    + "handler should not be set at this timming");
        }

        mAnimationHandler = new AnimationHandler(scheduler);
    }

    abstract float getAcceleration(float value, float velocity);
    abstract boolean isAtEquilibrium(float value, float velocity);
    abstract void setValueThreshold(float threshold);

    public interface OnAnimationEndListener {
        void onAnimationEnd(DynamicAnimation animation, boolean canceled, float value, float velocity);
    }

    public interface OnAnimationUpdateListener {
        void onAnimationUpdate(DynamicAnimation animation, float value, float velocity);
    }
}