package com.blakube.bktops.api.config;

public enum ConfigType {

    DATABASE("configuration/database.yml", "database.yml", "configuration", true),
    HOOKS("configuration/hooks.yml", "hooks.yml", "configuration", true),
    TOPS("configuration/tops.yml", "tops.yml", "configuration", false),
    LANG("lang/lang.yml", "lang.yml", "lang", true);

    private final String defaultPath;
    private final String resourceName;
    private final String parentFolder;
    private final boolean versioned;

    ConfigType(String defaultPath, String resourceName, String parentFolder, boolean versioned) {
        this.defaultPath = defaultPath;
        this.resourceName = resourceName;
        this.parentFolder = parentFolder;
        this.versioned = versioned;
    }

    public String getDefaultPath() {
        return defaultPath;
    }

    public String getResourceName() {
        return resourceName;
    }

    public String getParentFolder() {
        return parentFolder;
    }

    public boolean isVersioned() {return  versioned;}

    public boolean isFolder() {
        return defaultPath.endsWith("/");
    }
}
