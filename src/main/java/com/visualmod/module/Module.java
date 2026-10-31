package com.visualmod.module;

public abstract class Module {
    private final String name, description;
    private final Category category;
    private boolean enabled = false;
    private boolean hidden = false;
    private boolean permanent = false;

    public Module(String n, String d, Category c){name=n;description=d;category=c;}

    public void toggle(){
        if (permanent) return;
        if (com.visualmod.module.modules.util.SafeMode.isLocked()) {
            return;
        }
        if (hidden && !enabled) {
            return;
        }
        enabled=!enabled;
        if(enabled)onEnable();else onDisable();
        playToggleSound(enabled);
    }

    private void playToggleSound(boolean turnedOn) {
        try {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.getSoundManager() == null) return;
            net.minecraft.sound.SoundEvent event = turnedOn
                    ? com.visualmod.sound.ModSounds.BONK
                    : com.visualmod.sound.ModSounds.NUROFF;
            mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.ui(event, 1.0f));
        } catch (Exception e) {
            System.err.println("[VisualsMod] Не удалось проиграть звук переключения модуля: " + e);
            e.printStackTrace();
        }
    }

    public void onEnable(){}public void onDisable(){}public void onTick(){}
    public String getName(){return name;}
    public String getDescription(){return description;}
    public String getDisplayName(){return com.visualmod.lang.Lang.name(name);}
    public String getDisplayDescription(){return com.visualmod.lang.Lang.description(name, description);}
    public Category getCategory(){return category;}
    public boolean isEnabled(){return enabled;}

    public void setEnabled(boolean e){
        if (permanent) return;
        if (hidden && e) return;
        this.enabled=e;
    }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) {
        if (permanent) return;
        this.hidden = hidden;
        if (hidden && enabled) {
            setEnabled(false);
            onDisable();
        }
    }

    protected void makePermanent() {
        this.permanent = true;
        this.hidden = true;
        this.enabled = true;
    }

    public boolean isPermanent() { return permanent; }

    public enum Category {
        COMBAT("Combat"),MOVEMENT("Movement"),RENDER("Render"),
        PLAYER("Player"),UTIL("Util"),HUD("HUD"),COSMETICS("Cosmetics");
        private final String d;Category(String d){this.d=d;}
        public String getDisplayName(){return com.visualmod.lang.Lang.category(d);}
        public String getRawDisplayName(){return d;}
    }
}
