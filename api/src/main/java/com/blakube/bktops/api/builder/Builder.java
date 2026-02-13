package com.blakube.bktops.api.builder;

public interface Builder<T, K> {
    T build(K k);
}
