package com.zachduda.puuids;


/**
 * This class contains code from the EssentialsX project and team.
 * https://github.com/EssentialsX/Essentials/blob/3af931740b20507837276f87f9456221653ac43d/Essentials/src/main/java/com/earth2me/essentials/utils/EnumUtil.java
 */

import org.bukkit.Statistic;

import java.lang.reflect.Field;

public final class EnumUtil {

    // This class is used in the onEnable compare to see the native minecraft server play time vs PUUIDs playtime statistic.

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