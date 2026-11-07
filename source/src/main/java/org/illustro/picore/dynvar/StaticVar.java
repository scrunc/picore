package org.illustro.picore.dynvar;

public class StaticVar {

    public enum Scope {
        GLOBAL,
        PLAYER;

        public static Scope fromString(String str) {
            if (str == null) return GLOBAL;
            return str.trim().equalsIgnoreCase("player") ? PLAYER : GLOBAL;
        }
    }

    private final String name;
    private final Scope scope;
    private String defaultValue;
    private String globalValue;

    public StaticVar(String name, Scope scope, String defaultValue) {
        this.name = name.toLowerCase();
        this.scope = scope != null ? scope : Scope.GLOBAL;
        this.defaultValue = defaultValue != null ? defaultValue : "none";
        this.globalValue = this.defaultValue;
    }

    public String getName() {
        return name;
    }

    public Scope getScope() {
        return scope;
    }

    public boolean isPlayer() {
        return scope == Scope.PLAYER;
    }

    public boolean isGlobal() {
        return scope == Scope.GLOBAL;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue != null ? defaultValue : "none";
    }

    public String getGlobalValue() {
        return globalValue != null ? globalValue : defaultValue;
    }

    public void setGlobalValue(String globalValue) {
        this.globalValue = globalValue;
    }
}

