package com.zach_attack.puuids;

public class Quartet<uuid, plname, path, input> {

    private final String uuid;
    private final String plname;
    private final String path;
    private final Object input;

    public Quartet(String uuid, String plname, String path, Object input) {
        this.uuid = uuid;
        this.plname = plname;
        this.path = path;
        this.input = input;
    }

    public String getUUID() { return uuid; }
    public String getPlugin() { return plname; }
    public String getPath() { return path; }
    public Object getData() { return input; }
}