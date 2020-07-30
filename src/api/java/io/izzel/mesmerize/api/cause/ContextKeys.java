package io.izzel.mesmerize.api.cause;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

public final class ContextKeys {

    public static final ContextKey<LivingEntity> SOURCE = () -> "source";
    public static final ContextKey<Entity> TARGET = () -> "target";

    private ContextKeys() {
        throw new RuntimeException();
    }
}
