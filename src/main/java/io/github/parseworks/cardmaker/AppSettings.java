package io.github.parseworks.cardmaker;

/**
 * Holds application-wide settings and state.
 */
public class AppSettings {
    /** The file path of the last deck opened by the user. */
    private String lastOpenedDeckPath;

    /** @return the last opened deck path */
    public String getLastOpenedDeckPath() {
        return lastOpenedDeckPath;
    }

    /** @param lastOpenedDeckPath the last opened deck path to set */
    public void setLastOpenedDeckPath(String lastOpenedDeckPath) {
        this.lastOpenedDeckPath = lastOpenedDeckPath;
    }
}
