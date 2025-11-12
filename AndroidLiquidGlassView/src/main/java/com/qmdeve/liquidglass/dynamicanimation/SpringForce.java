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

import androidx.annotation.FloatRange;
import androidx.annotation.RestrictTo;

public final class SpringForce implements Force {
    public static final float STIFFNESS_HIGH = 10_000f;
    public static final float STIFFNESS_MEDIUM = 1500f;
    public static final float STIFFNESS_LOW = 200f;
    public static final float STIFFNESS_VERY_LOW = 50f;

    public static final float DAMPING_RATIO_HIGH_BOUNCY = 0.2f;
    public static final float DAMPING_RATIO_MEDIUM_BOUNCY = 0.5f;
    public static final float DAMPING_RATIO_LOW_BOUNCY = 0.75f;
    public static final float DAMPING_RATIO_NO_BOUNCY = 1f;
    private static final double VELOCITY_THRESHOLD_MULTIPLIER = 1000.0 / 16.0;
    double mNaturalFreq = Math.sqrt(STIFFNESS_MEDIUM);
    double mDampingRatio = DAMPING_RATIO_MEDIUM_BOUNCY;
    private static final double UNSET = Double.MAX_VALUE;
    private boolean mInitialized = false;
    private double mValueThreshold;
    private double mVelocityThreshold;
    private double mGammaPlus;
    private double mGammaMinus;
    private double mDampedFreq;
    private double mFinalPosition = UNSET;
    private final DynamicAnimation.MassState mMassState = new DynamicAnimation.MassState();

    public SpringForce() {}

    public SpringForce(float finalPosition) {
        mFinalPosition = finalPosition;
    }

    public SpringForce setStiffness(
            @FloatRange(from = 0.0, fromInclusive = false) float stiffness) {
        if (stiffness <= 0) {
            throw new IllegalArgumentException("Spring stiffness constant must be positive.");
        }
        mNaturalFreq = Math.sqrt(stiffness);
        mInitialized = false;
        return this;
    }

    public float getStiffness() {
        return (float) (mNaturalFreq * mNaturalFreq);
    }

    public SpringForce setDampingRatio(@FloatRange(from = 0.0) float dampingRatio) {
        if (dampingRatio < 0) {
            throw new IllegalArgumentException("Damping ratio must be non-negative");
        }
        mDampingRatio = dampingRatio;
        mInitialized = false;
        return this;
    }

    public float getDampingRatio() {
        return (float) mDampingRatio;
    }

    public SpringForce setFinalPosition(float finalPosition) {
        mFinalPosition = finalPosition;
        return this;
    }

    public float getFinalPosition() {
        return (float) mFinalPosition;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public float getAcceleration(float lastDisplacement, float lastVelocity) {

        lastDisplacement -= getFinalPosition();

        double k = mNaturalFreq * mNaturalFreq;
        double c = 2 * mNaturalFreq * mDampingRatio;

        return (float) (-k * lastDisplacement - c * lastVelocity);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public boolean isAtEquilibrium(float value, float velocity) {
        if (Math.abs(velocity) < mVelocityThreshold
                && Math.abs(value - getFinalPosition()) < mValueThreshold) {
            return true;
        }
        return false;
    }

    private void init() {
        if (mInitialized) {
            return;
        }

        if (mFinalPosition == UNSET) {
            throw new IllegalStateException("Error: Final position of the spring must be"
                    + " set before the animation starts");
        }

        if (mDampingRatio > 1) {
            mGammaPlus = -mDampingRatio * mNaturalFreq
                    + mNaturalFreq * Math.sqrt(mDampingRatio * mDampingRatio - 1);
            mGammaMinus = -mDampingRatio * mNaturalFreq
                    - mNaturalFreq * Math.sqrt(mDampingRatio * mDampingRatio - 1);
        } else if (mDampingRatio >= 0 && mDampingRatio < 1) {
            mDampedFreq = mNaturalFreq * Math.sqrt(1 - mDampingRatio * mDampingRatio);
        }

        mInitialized = true;
    }

    DynamicAnimation.MassState updateValues(double lastDisplacement, double lastVelocity, long timeElapsed) {
        init();

        double deltaT = timeElapsed / 1000d;
        lastDisplacement -= mFinalPosition;
        double displacement;
        double currentVelocity;
        if (mDampingRatio > 1) {
            double coeffA =  lastDisplacement - (mGammaMinus * lastDisplacement - lastVelocity)
                    / (mGammaMinus - mGammaPlus);
            double coeffB =  (mGammaMinus * lastDisplacement - lastVelocity)
                    / (mGammaMinus - mGammaPlus);
            displacement = coeffA * Math.pow(Math.E, mGammaMinus * deltaT)
                    + coeffB * Math.pow(Math.E, mGammaPlus * deltaT);
            currentVelocity = coeffA * mGammaMinus * Math.pow(Math.E, mGammaMinus * deltaT)
                    + coeffB * mGammaPlus * Math.pow(Math.E, mGammaPlus * deltaT);
        } else if (mDampingRatio == 1) {
            double coeffA = lastDisplacement;
            double coeffB = lastVelocity + mNaturalFreq * lastDisplacement;
            displacement = (coeffA + coeffB * deltaT) * Math.pow(Math.E, -mNaturalFreq * deltaT);
            currentVelocity = (coeffA + coeffB * deltaT) * Math.pow(Math.E, -mNaturalFreq * deltaT)
                    * -mNaturalFreq + coeffB * Math.pow(Math.E, -mNaturalFreq * deltaT);
        } else {
            double cosCoeff = lastDisplacement;
            double sinCoeff = (1 / mDampedFreq) * (mDampingRatio * mNaturalFreq
                    * lastDisplacement + lastVelocity);
            displacement = Math.pow(Math.E, -mDampingRatio * mNaturalFreq * deltaT)
                    * (cosCoeff * Math.cos(mDampedFreq * deltaT)
                    + sinCoeff * Math.sin(mDampedFreq * deltaT));
            currentVelocity = displacement * -mNaturalFreq * mDampingRatio
                    + Math.pow(Math.E, -mDampingRatio * mNaturalFreq * deltaT)
                    * (-mDampedFreq * cosCoeff * Math.sin(mDampedFreq * deltaT)
                    + mDampedFreq * sinCoeff * Math.cos(mDampedFreq * deltaT));
        }

        mMassState.mValue = (float) (displacement + mFinalPosition);
        mMassState.mVelocity = (float) currentVelocity;
        return mMassState;
    }

    void setValueThreshold(double threshold) {
        mValueThreshold = Math.abs(threshold);
        mVelocityThreshold = mValueThreshold * VELOCITY_THRESHOLD_MULTIPLIER;
    }
}