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

import android.util.FloatProperty;

import androidx.annotation.RequiresApi;

public abstract class FloatPropertyCompat<T> {
    final String mPropertyName;
    public FloatPropertyCompat(String name) {
        mPropertyName = name;
    }

    @RequiresApi(24)
    public static <T> FloatPropertyCompat<T> createFloatPropertyCompat(final FloatProperty<T> property) {
        return new FloatPropertyCompat<T>(property.getName()) {
            @Override
            public float getValue(T object) {
                return property.get(object);
            }

            @Override
            public void setValue(T object, float value) {
                property.setValue(object, value);
            }
        };
    }

    public abstract float getValue(T object);
    public abstract void setValue(T object, float value);
}