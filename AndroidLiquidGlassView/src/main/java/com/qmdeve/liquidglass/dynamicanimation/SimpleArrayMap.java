package com.qmdeve.liquidglass.dynamicanimation;

import java.util.ConcurrentModificationException;
import java.util.Map;

public class SimpleArrayMap<K, V> {
    private static final boolean DEBUG = false;
    private static final String TAG = "ArrayMap";
    private static final boolean CONCURRENT_MODIFICATION_EXCEPTIONS = true;
    private static final int BASE_SIZE = 4;
    private static final int[] EMPTY_INTS = new int[0];
    private static final Object[] EMPTY_OBJECTS = new Object[0];
    private int[] hashes;
    private Object[] array;
    private int size;

    public SimpleArrayMap() {
        this(0);
    }

    public SimpleArrayMap(int capacity) {
        if (capacity == 0) {
            hashes = EMPTY_INTS;
            array = EMPTY_OBJECTS;
        } else {
            hashes = new int[capacity];
            array = new Object[capacity << 1];
        }
        size = 0;
    }

    public SimpleArrayMap(SimpleArrayMap<? extends K, ? extends V> map) {
        this();
        if (map != null) {
            putAll(map);
        }
    }

    private int indexOf(Object key, int hash) {
        final int n = size;

        if (n == 0) {
            return ~0;
        }

        int index = ContainerHelpers.binarySearch(hashes, n, hash);
        if (index < 0) {
            return index;
        }

        if (key.equals(array[index << 1])) {
            return index;
        }

        int end = index + 1;
        while (end < n && hashes[end] == hash) {
            if (key.equals(array[end << 1])) return end;
            end++;
        }

        int i = index - 1;
        while (i >= 0 && hashes[i] == hash) {
            if (key.equals(array[i << 1])) {
                return i;
            }
            i--;
        }

        return ~end;
    }

    private int indexOfNull() {
        final int n = size;

        if (n == 0) {
            return ~0;
        }

        int index = ContainerHelpers.binarySearch(hashes, n, 0);

        if (index < 0) {
            return index;
        }

        if (array[index << 1] == null) {
            return index;
        }

        int end = index + 1;
        while (end < n && hashes[end] == 0) {
            if (array[end << 1] == null) return end;
            end++;
        }

        int i = index - 1;
        while (i >= 0 && hashes[i] == 0) {
            if (array[i << 1] == null) return i;
            i--;
        }

        return ~end;
    }

    public void clear() {
        if (size > 0) {
            hashes = EMPTY_INTS;
            array = EMPTY_OBJECTS;
            size = 0;
        }
        if (CONCURRENT_MODIFICATION_EXCEPTIONS && size > 0) {
            throw new ConcurrentModificationException();
        }
    }

    public void ensureCapacity(int minimumCapacity) {
        final int osize = size;
        if (hashes.length < minimumCapacity) {
            hashes = copyOf(hashes, minimumCapacity);
            array = copyOf(array, minimumCapacity * 2);
        }
        if (CONCURRENT_MODIFICATION_EXCEPTIONS && size != osize) {
            throw new ConcurrentModificationException();
        }
    }

    public boolean containsKey(Object key) {
        return indexOfKey(key) >= 0;
    }

    public int indexOfKey(Object key) {
        return key == null ? indexOfNull() : indexOf(key, key.hashCode());
    }

    int indexOfValue(Object value) {
        final int n = size * 2;
        final Object[] array = this.array;
        if (value == null) {
            for (int i = 1; i < n; i += 2) {
                if (array[i] == null) {
                    return i >> 1;
                }
            }
        } else {
            for (int i = 1; i < n; i += 2) {
                if (value.equals(array[i])) {
                    return i >> 1;
                }
            }
        }
        return -1;
    }

    public boolean containsValue(Object value) {
        return indexOfValue(value) >= 0;
    }

    public V get(Object key) {
        return getOrDefaultInternal(key, null);
    }

    public V getOrDefault(Object key, V defaultValue) {
        return getOrDefaultInternal(key, defaultValue);
    }

    @SuppressWarnings("unchecked")
    private <T extends V> T getOrDefaultInternal(Object key, T defaultValue) {
        int index = indexOfKey((K) key);
        if (index >= 0) {
            return (T) array[(index << 1) + 1];
        } else {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public K keyAt(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Expected index to be within 0..size()-1, but was " + index);
        }
        return (K) array[index << 1];
    }

    @SuppressWarnings("unchecked")
    public V valueAt(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Expected index to be within 0..size()-1, but was " + index);
        }
        return (V) array[(index << 1) + 1];
    }

    @SuppressWarnings("unchecked")
    public V setValueAt(int index, V value) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Expected index to be within 0..size()-1, but was " + index);
        }

        int indexInArray = (index << 1) + 1;
        V old = (V) array[indexInArray];
        array[indexInArray] = value;
        return old;
    }

    public boolean isEmpty() {
        return size <= 0;
    }

    public V put(K key, V value) {
        final int osize = size;
        final int hash = key == null ? 0 : key.hashCode();
        int index = key == null ? indexOfNull() : indexOf(key, hash);

        if (index >= 0) {
            index = (index << 1) + 1;
            @SuppressWarnings("unchecked")
            V old = (V) array[index];
            array[index] = value;
            return old;
        }

        index = ~index;
        if (osize >= hashes.length) {
            int n = osize >= (BASE_SIZE * 2) ? osize + (osize >> 1) :
                    osize >= BASE_SIZE ? (BASE_SIZE * 2) : BASE_SIZE;

            if (DEBUG) {
                System.out.println(TAG + " put: grow from " + hashes.length + " to " + n);
            }

            hashes = copyOf(hashes, n);
            array = copyOf(array, n << 1);

            if (CONCURRENT_MODIFICATION_EXCEPTIONS && osize != size) {
                throw new ConcurrentModificationException();
            }
        }

        if (index < osize) {
            if (DEBUG) {
                System.out.println(TAG + " put: move " + index + "-" + (osize - index) + " to " + (index + 1));
            }

            System.arraycopy(hashes, index, hashes, index + 1, osize - index);
            System.arraycopy(array, index << 1, array, (index + 1) << 1, (size - index) << 1);
        }

        if (CONCURRENT_MODIFICATION_EXCEPTIONS && (osize != size || index >= hashes.length)) {
            throw new ConcurrentModificationException();
        }

        hashes[index] = hash;
        array[index << 1] = key;
        array[(index << 1) + 1] = value;
        size++;
        return null;
    }

    public void putAll(SimpleArrayMap<? extends K, ? extends V> map) {
        final int n = map.size;
        ensureCapacity(size + n);
        if (size == 0) {
            if (n > 0) {
                System.arraycopy(map.hashes, 0, hashes, 0, n);
                System.arraycopy(map.array, 0, array, 0, n << 1);
                size = n;
            }
        } else {
            for (int i = 0; i < n; i++) {
                put(map.keyAt(i), map.valueAt(i));
            }
        }
    }

    public V putIfAbsent(K key, V value) {
        V mapValue = get(key);
        if (mapValue == null) {
            mapValue = put(key, value);
        }
        return mapValue;
    }

    public V remove(Object key) {
        int index = indexOfKey(key);
        return index >= 0 ? removeAt(index) : null;
    }

    public boolean remove(Object key, Object value) {
        int index = indexOfKey(key);
        if (index >= 0) {
            V mapValue = valueAt(index);
            if (value.equals(mapValue)) {
                removeAt(index);
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public V removeAt(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Expected index to be within 0..size()-1, but was " + index);
        }

        Object old = array[(index << 1) + 1];
        final int osize = size;
        if (osize <= 1) {
            if (DEBUG) {
                System.out.println(TAG + " remove: shrink from " + hashes.length + " to 0");
            }
            clear();
        } else {
            final int nsize = osize - 1;
            if (hashes.length > (BASE_SIZE * 2) && osize < hashes.length / 3) {
                int n = osize > (BASE_SIZE * 2) ? osize + (osize >> 1) : BASE_SIZE * 2;

                if (DEBUG) {
                    System.out.println(TAG + " remove: shrink from " + hashes.length + " to " + n);
                }

                int[] ohashes = hashes;
                Object[] oarray = array;
                hashes = new int[n];
                array = new Object[n << 1];

                if (CONCURRENT_MODIFICATION_EXCEPTIONS && osize != size) {
                    throw new ConcurrentModificationException();
                }

                if (index > 0) {
                    System.arraycopy(ohashes, 0, hashes, 0, index);
                    System.arraycopy(oarray, 0, array, 0, index << 1);
                }

                if (index < nsize) {
                    System.arraycopy(ohashes, index + 1, hashes, index, nsize - index);
                    System.arraycopy(oarray, (index + 1) << 1, array, index << 1, (nsize - index) << 1);
                }
            } else {
                if (index < nsize) {
                    System.arraycopy(hashes, index + 1, hashes, index, nsize - index);
                    System.arraycopy(array, (index + 1) << 1, array, index << 1, (nsize - index) << 1);
                }
                array[nsize << 1] = null;
                array[(nsize << 1) + 1] = null;
            }
            if (CONCURRENT_MODIFICATION_EXCEPTIONS && osize != size) {
                throw new ConcurrentModificationException();
            }
            size = nsize;
        }
        return (V) old;
    }

    public V replace(K key, V value) {
        int index = indexOfKey(key);
        return index >= 0 ? setValueAt(index, value) : null;
    }

    public boolean replace(K key, V oldValue, V newValue) {
        int index = indexOfKey(key);
        if (index >= 0) {
            V mapValue = valueAt(index);
            if (oldValue.equals(mapValue)) {
                setValueAt(index, newValue);
                return true;
            }
        }
        return false;
    }

    public int size() {
        return size;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        try {
            if (other instanceof SimpleArrayMap) {
                @SuppressWarnings("unchecked")
                SimpleArrayMap<Object, Object> otherSimpleArrayMap = (SimpleArrayMap<Object, Object>) other;
                if (size() != otherSimpleArrayMap.size()) {
                    return false;
                }

                for (int i = 0; i < size; i++) {
                    K key = keyAt(i);
                    V mine = valueAt(i);
                    Object theirs = otherSimpleArrayMap.get(key);
                    if (mine == null) {
                        if (theirs != null || !otherSimpleArrayMap.containsKey(key)) {
                            return false;
                        }
                    } else if (!mine.equals(theirs)) {
                        return false;
                    }
                }
                return true;
            } else if (other instanceof Map) {
                Map<?, ?> otherMap = (Map<?, ?>) other;
                if (size() != otherMap.size()) {
                    return false;
                }
                for (int i = 0; i < size; i++) {
                    K key = keyAt(i);
                    V mine = valueAt(i);
                    Object theirs = otherMap.get(key);
                    if (mine == null) {
                        if (theirs != null || !otherMap.containsKey(key)) {
                            return false;
                        }
                    } else if (!mine.equals(theirs)) {
                        return false;
                    }
                }
                return true;
            }
        } catch (NullPointerException | ClassCastException ignored) {}
        return false;
    }

    @Override
    public int hashCode() {
        int[] hashes = this.hashes;
        Object[] array = this.array;
        int result = 0;
        int i = 0;
        int v = 1;
        int s = size;
        while (i < s) {
            Object value = array[v];
            result += hashes[i] ^ (value == null ? 0 : value.hashCode());
            i++;
            v += 2;
        }
        return result;
    }

    @Override
    public String toString() {
        if (isEmpty()) {
            return "{}";
        }

        StringBuilder buffer = new StringBuilder(size * 28);
        buffer.append('{');
        for (int i = 0; i < size; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            Object key = keyAt(i);
            if (key != this) {
                buffer.append(key);
            } else {
                buffer.append("(this Map)");
            }
            buffer.append('=');
            Object value = valueAt(i);
            if (value != this) {
                buffer.append(value);
            } else {
                buffer.append("(this Map)");
            }
        }
        buffer.append('}');
        return buffer.toString();
    }

    private static int[] copyOf(int[] original, int newLength) {
        int[] result = new int[newLength];
        System.arraycopy(original, 0, result, 0, Math.min(original.length, newLength));
        return result;
    }

    private static Object[] copyOf(Object[] original, int newLength) {
        Object[] result = new Object[newLength];
        System.arraycopy(original, 0, result, 0, Math.min(original.length, newLength));
        return result;
    }

    private static class ContainerHelpers {
        static int binarySearch(int[] array, int size, int value) {
            int lo = 0;
            int hi = size - 1;

            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                int midVal = array[mid];

                if (midVal < value) {
                    lo = mid + 1;
                } else if (midVal > value) {
                    hi = mid - 1;
                } else {
                    return mid;
                }
            }
            return ~lo;
        }
    }
}