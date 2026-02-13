package com.blakube.bktops.api.builder.serializer;

public interface Serializer<T, K> {
    T serialize(K k);
}