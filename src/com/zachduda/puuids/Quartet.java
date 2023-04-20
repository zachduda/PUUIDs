package com.zachduda.puuids;

import com.zachduda.puuids.api.PUUIDS;
import com.zachduda.puuids.api.PUUIDS.SavePriority;

public class Quartet<uuid, plname, path, input, id, save_priority> {

    private final String uuid;
    private final String plname;
    private final String path;
    private final Object input;
    private final int id;

    private final SavePriority sp;

    public Quartet(String uuid, String plname, String path, Object input, int id, SavePriority sp) {
        this.uuid = uuid;
        this.plname = plname;
        this.path = path;
        this.input = input;
        this.id = id;
        this.sp = sp;
    }

    public String getUUID() {
        return uuid;
    }

    public String getPlugin() {
        return plname;
    }

    public String getPath() {
        return path;
    }

    public Object getData() {
        return input;
    }

    public int getId() {
        return id;
    }

    public SavePriority getSp() {
        return sp;
    }
}