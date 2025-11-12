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

import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;

public class AnimationHandler {
    interface AnimationFrameCallback {
        boolean doAnimationFrame(long frameTime);
    }

    private class AnimationCallbackDispatcher {
        void dispatchAnimationFrame() {
            mCurrentFrameTime = SystemClock.uptimeMillis();
            AnimationHandler.this.doAnimationFrame(mCurrentFrameTime);
            if (mAnimationCallbacks.size() > 0) {
                mScheduler.postFrameCallback(mRunnable);
            }
        }
    }

    private static final ThreadLocal<AnimationHandler> sAnimatorHandler = new ThreadLocal<>();
    private final SimpleArrayMap<AnimationFrameCallback, Long> mDelayedCallbackStartTime = new SimpleArrayMap<>();
    final ArrayList<AnimationFrameCallback> mAnimationCallbacks = new ArrayList<>();
    private final AnimationCallbackDispatcher mCallbackDispatcher = new AnimationCallbackDispatcher();
    private final Runnable mRunnable = () -> mCallbackDispatcher.dispatchAnimationFrame();
    private FrameCallbackScheduler mScheduler;
    long mCurrentFrameTime = 0;
    private boolean mListDirty = false;
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @VisibleForTesting
    public float mDurationScale = 1.0f;
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @VisibleForTesting
    public @Nullable DurationScaleChangeListener mDurationScaleChangeListener;

    static AnimationHandler getInstance() {
        if (sAnimatorHandler.get() == null) {
            AnimationHandler handler = new AnimationHandler(
                    new FrameCallbackScheduler16());
            sAnimatorHandler.set(handler);
        }
        return sAnimatorHandler.get();
    }

    public AnimationHandler(@NonNull FrameCallbackScheduler scheduler) {
        mScheduler = scheduler;
    }

    void addAnimationFrameCallback(final AnimationFrameCallback callback, long delay) {
        if (mAnimationCallbacks.size() == 0) {
            mScheduler.postFrameCallback(mRunnable);
            if (Build.VERSION.SDK_INT >= 33) {
                mDurationScale = ValueAnimator.getDurationScale();
                if (mDurationScaleChangeListener == null) {
                    mDurationScaleChangeListener = new DurationScaleChangeListener33();
                }
                mDurationScaleChangeListener.register();
            }
        }
        if (!mAnimationCallbacks.contains(callback)) {
            mAnimationCallbacks.add(callback);
        }

        if (delay > 0) {
            mDelayedCallbackStartTime.put(callback, (SystemClock.uptimeMillis() + delay));
        }
    }

    void removeCallback(AnimationFrameCallback callback) {
        mDelayedCallbackStartTime.remove(callback);
        int id = mAnimationCallbacks.indexOf(callback);
        if (id >= 0) {
            mAnimationCallbacks.set(id, null);
            mListDirty = true;
        }
    }

    void doAnimationFrame(long frameTime) {
        long currentTime = SystemClock.uptimeMillis();
        for (int i = 0; i < mAnimationCallbacks.size(); i++) {
            final AnimationFrameCallback callback = mAnimationCallbacks.get(i);
            if (callback == null) {
                continue;
            }
            if (isCallbackDue(callback, currentTime)) {
                callback.doAnimationFrame(frameTime);
            }
        }
        cleanUpList();
    }

    boolean isCurrentThread() {
        return mScheduler.isCurrentThread();
    }

    private boolean isCallbackDue(AnimationFrameCallback callback, long currentTime) {
        Long startTime = mDelayedCallbackStartTime.get(callback);
        if (startTime == null) {
            return true;
        }
        if (startTime < currentTime) {
            mDelayedCallbackStartTime.remove(callback);
            return true;
        }
        return false;
    }

    private void cleanUpList() {
        if (mListDirty) {
            for (int i = mAnimationCallbacks.size() - 1; i >= 0; i--) {
                if (mAnimationCallbacks.get(i) == null) {
                    mAnimationCallbacks.remove(i);
                }
            }
            if (mAnimationCallbacks.size() == 0) {
                if (Build.VERSION.SDK_INT >= 33) {
                    mDurationScaleChangeListener.unregister();
                }
            }
            mListDirty = false;
        }
    }

    @NonNull
    FrameCallbackScheduler getScheduler() {
        return mScheduler;
    }

    static final class FrameCallbackScheduler16 implements FrameCallbackScheduler {
        private final Choreographer mChoreographer = Choreographer.getInstance();
        private final Looper mLooper = Looper.myLooper();

        @Override
        public void postFrameCallback(@NonNull Runnable frameCallback) {
            mChoreographer.postFrameCallback(time -> frameCallback.run());
        }

        @Override
        public boolean isCurrentThread() {
            return Thread.currentThread() == mLooper.getThread();
        }
    }

    @VisibleForTesting
    public float getDurationScale() {
        return mDurationScale;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @RequiresApi(api = 33)
    @VisibleForTesting
    public class DurationScaleChangeListener33 implements DurationScaleChangeListener {
        ValueAnimator.DurationScaleChangeListener mListener;

        @Override
        public boolean register() {
            if (mListener == null) {
                mListener = scale -> AnimationHandler.this.mDurationScale = scale;
                return ValueAnimator.registerDurationScaleChangeListener(mListener);
            }
            return true;
        }

        @Override
        public boolean unregister() {
            boolean unregistered = ValueAnimator.unregisterDurationScaleChangeListener(mListener);
            mListener = null;
            return unregistered;
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @VisibleForTesting
    public interface DurationScaleChangeListener {
        boolean register();
        boolean unregister();
    }
}