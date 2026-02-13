package com.blakube.bktops.api.builder.mapper;

public interface Mapper<T, K> {
    T map(K k);
}
