package com.blakube.bktops.plugin.provider;

import org.jetbrains.annotations.NotNull;






public interface DetectableValueKind {

    @NotNull ValueKind getDetectedValueKind();
}
