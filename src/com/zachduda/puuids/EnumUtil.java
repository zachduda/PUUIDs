package com.zachduda.puuids;

import org.bukkit.Statistic;
import java.lang.reflect.Field;

public final class EnumUtil {

    // This class is used in the onEnable compare to see the native minecraft server play time vs puuids playtime statistic.

    private EnumUtil() {
    }

    public static <T extends Enum<T>> T valueOf(final Class<T> enumClass, final String... names) {
        for (final String name : names) {
            try {
                final Field enumField = enumClass.getDeclaredField(name);

                if (enumField.isEnumConstant()) {
                    return (T) enumField.get(null);
                }
            } catch (final NoSuchFieldException | IllegalAccessException ignored) {
            }
        }

        return null;
    }

    public static Statistic getStatistic(final String... names) {
        return valueOf(Statistic.class, names);
    }

}