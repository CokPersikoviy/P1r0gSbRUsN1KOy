package ru.wilyfox.client.hud.config;

public class RenderConfig {
    public boolean debug = false;
    public boolean hideBlockBreakParticles = false;
    public boolean hideLightningEffect = false;
    public boolean hideHurtCameraShake = false;
    public boolean staticHand = false;
    public boolean hideFireOverlay = false;
    public boolean hideFirstPersonCosmetics = true;
    public boolean modUserBadge = true;                       // frog badge on FrogHelper users' nametags
    public boolean modUserMesh = true;                        // Socials: outgoing beacon + PM mesh participation
    public boolean toneDownChat = false;
    public boolean copyChatMessages = true;
    public boolean fullMessageCopy = false;
    public boolean chatTimestamps = true;
    public int extraChatHistoryLines = 200;
    public boolean autoThanks = true;
    public boolean showCurrentServerInTab = true;
    public boolean dungeonDecorationHighlight = true;
    public boolean usefulItemsHighlight = true;
    public boolean showAlchemyIngredientMarkers = true;
    public boolean unclutterWidgets = true;

    // Frost HUD surface language.
    public WidgetChrome widgetChrome = WidgetChrome.FROST;   // BARE / SOLID / FROST intensity
    public boolean nativeRenderer = false;                    // fall back to GuiGraphics (no blur/rounding)
}
