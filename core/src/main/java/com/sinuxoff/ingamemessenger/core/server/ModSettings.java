package com.sinuxoff.ingamemessenger.core.server;

/** Server config that can also be changed and reloaded in game (/messenger ...). */
public interface ModSettings extends ServerSettings {
    /** Rereads the file. */
    void reload();

    void setTyping(boolean on);

    void setVoiceOn(boolean on);

    void setVoiceMaxSeconds(int seconds);

    void setVoiceKeepDays(int days);
}
