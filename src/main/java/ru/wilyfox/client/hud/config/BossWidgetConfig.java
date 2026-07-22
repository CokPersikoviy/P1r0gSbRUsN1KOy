package ru.wilyfox.client.hud.config;

public class BossWidgetConfig {
    /**
     * Highest boss level the mod ships knowledge of. Used as the default max-level filter and as
     * the floor for the dynamic slider ceiling — the effective ceiling grows past this automatically
     * once the protocol reports higher-level bosses (see DiamondWorldProtocolClient#getHighestKnownBossLevel).
     */
    public static final int MAX_LEVEL_CEILING = 590;

    public boolean active = true;
    public boolean fullAligment = false;
    public BossTimerSourceMode sourceMode = BossTimerSourceMode.PROTOCOL_PREFERRED;

    public int maxBosses = 5;

    public int minLevel = 15;
    public int maxLevel = MAX_LEVEL_CEILING;

    public boolean showName = true;
    public boolean showIcons = true;
    public boolean showLevel = true;
    public boolean showTimer = true;
    public boolean showCollectibles = true;

    /** How long (seconds) a spawned boss keeps counting into the negative before it drops from the timer. */
    public int postSpawnShowSeconds = 30;
    /** Keep a spawned boss shown until it respawns (a new timer arrives), ignoring postSpawnShowSeconds. */
    public boolean showSpawnedUntilKilled = false;
}
