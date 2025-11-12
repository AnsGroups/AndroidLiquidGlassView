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

import android.util.AndroidRuntimeException;

import androidx.annotation.MainThread;

public final class SpringAnimation extends DynamicAnimation<SpringAnimation> {

    private SpringForce mSpring = null;
    private float mPendingPosition = UNSET;
    private static final float UNSET = Float.MAX_VALUE;
    private boolean mEndRequested = false;

    public SpringAnimation(FloatValueHolder floatValueHolder) {
        super(floatValueHolder);
    }

    public SpringAnimation(FloatValueHolder floatValueHolder, float finalPosition) {
        super(floatValueHolder);
        mSpring = new SpringForce(finalPosition);
    }

    public <K> SpringAnimation(K object, FloatPropertyCompat<K> property) {
        super(object, property);
    }

    public <K> SpringAnimation(K object, FloatPropertyCompat<K> property,
                               float finalPosition) {
        super(object, property);
        mSpring = new SpringForce(finalPosition);
    }

    public SpringForce getSpring() {
        return mSpring;
    }

    public SpringAnimation setSpring(SpringForce force) {
        mSpring = force;
        return this;
    }

    @MainThread
    @Override
    public void start() {
        sanityCheck();
        mSpring.setValueThreshold(getValueThreshold());
        super.start();
    }

    public void animateToFinalPosition(float finalPosition) {
        if (isRunning()) {
            mPendingPosition = finalPosition;
        } else {
            if (mSpring == null) {
                mSpring = new SpringForce(finalPosition);
            }
            mSpring.setFinalPosition(finalPosition);
            start();
        }
    }

    @MainThread
    @Override
    public void cancel() {
        super.cancel();
        if (mPendingPosition != UNSET) {
            if (mSpring == null) {
                mSpring = new SpringForce(mPendingPosition);
            } else {
                mSpring.setFinalPosition(mPendingPosition);
            }
            mPendingPosition = UNSET;
        }
    }

    public void skipToEnd() {
        if (!canSkipToEnd()) {
            throw new UnsupportedOperationException("Spring animations can only come to an end"
                    + " when there is damping");
        }
        if (!getAnimationHandler().isCurrentThread()) {
            throw new AndroidRuntimeException("Animations may only be started on the same thread "
                    + "as the animation handler");
        }
        if (mRunning) {
            mEndRequested = true;
        }
    }

    public boolean canSkipToEnd() {
        return mSpring.mDampingRatio > 0;
    }

    private void sanityCheck() {
        if (mSpring == null) {
            throw new UnsupportedOperationException("Incomplete SpringAnimation: Either final"
                    + " position or a spring force needs to be set.");
        }
        double finalPosition = mSpring.getFinalPosition();
        if (finalPosition > mMaxValue) {
            throw new UnsupportedOperationException("Final position of the spring cannot be greater"
                    + " than the max value.");
        } else if (finalPosition < mMinValue) {
            throw new UnsupportedOperationException("Final position of the spring cannot be less"
                    + " than the min value.");
        }
    }

    @Override
    boolean updateValueAndVelocity(long deltaT) {
        if (mEndRequested) {
            if (mPendingPosition != UNSET) {
                mSpring.setFinalPosition(mPendingPosition);
                mPendingPosition = UNSET;
            }
            mValue = mSpring.getFinalPosition();
            mVelocity = 0;
            mEndRequested = false;
            return true;
        }

        if (mPendingPosition != UNSET) {
            MassState massState = mSpring.updateValues(mValue, mVelocity, deltaT / 2);
            mSpring.setFinalPosition(mPendingPosition);
            mPendingPosition = UNSET;

            massState = mSpring.updateValues(massState.mValue, massState.mVelocity, deltaT / 2);
            mValue = massState.mValue;
            mVelocity = massState.mVelocity;

        } else {
            MassState massState = mSpring.updateValues(mValue, mVelocity, deltaT);
            mValue = massState.mValue;
            mVelocity = massState.mVelocity;
        }

        mValue = Math.max(mValue, mMinValue);
        mValue = Math.min(mValue, mMaxValue);

        if (isAtEquilibrium(mValue, mVelocity)) {
            mValue = mSpring.getFinalPosition();
            mVelocity = 0f;
            return true;
        }
        return false;
    }

    @Override
    float getAcceleration(float value, float velocity) {
        return mSpring.getAcceleration(value, velocity);
    }

    @Override
    boolean isAtEquilibrium(float value, float velocity) {
        return mSpring.isAtEquilibrium(value, velocity);
    }

    @Override
    void setValueThreshold(float threshold) {
    }
}