package com.zachduda.PUUIDs;

public class Quartet<uuid, plname, path, input, id> {

    private final String uuid;
    private final String plname;
    private final String path;
    private final Object input;
    private final int id;

    public Quartet(String uuid, String plname, String path, Object input, int id) {
        this.uuid = uuid;
        this.plname = plname;
        this.path = path;
        this.input = input;
        this.id = id;
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
}