package com.visualmod.module;

public abstract class Module {
    private final String name, description;
    private final Category category;
    /** Что модуль делает прямо сейчас: сокрытие или SafeMode могут расходиться с намерением. */
    private boolean enabled = false;
    /** Намерение пользователя. Только оно попадает в конфиг, поэтому временное сокрытие настройку не стирает. */
    private boolean desired = false;
    private boolean hidden = false;
    private boolean permanent = false;

    /**
     * true, пока состояние меняет код, а не пользователь: снятие сокрытия, загрузка конфига, SafeMode.
     * Модули, чей onEnable открывает редактор, обязаны это проверять — иначе окно всплывает посреди боя,
     * когда сервер снял блок-лист.
     */
    private static boolean programmatic = false;

    public static boolean isProgrammatic() { return programmatic; }

    public Module(String n, String d, Category c){name=n;description=d;category=c;}

    public void toggle(){
        if (permanent) return;
        if (com.visualmod.module.modules.util.SafeMode.isLocked()) {
            return;
        }
        if (hidden) {
            return;
        }
        desired = !desired;
        apply(false);
        playToggleSound(desired);
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

    /** Намерение пользователя — именно его пишет и с него читает конфиг. */
    public boolean getDesired(){return desired;}

    public void setEnabled(boolean e){
        if (permanent) return;
        desired = e;
        apply(true);
    }

    /** Перечитать намерение после смены внешних блокировщиков (скрытие, SafeMode). */
    public void resync(){
        if (permanent) return;
        apply(true);
    }

    public boolean isHidden() { return hidden; }

    /**
     * Сокрытие убирает модуль из GUI и выключает его, но намерение сохраняет: снятое сокрытие возвращает
     * модуль включённым, если пользователь его включал.
     */
    public void setHidden(boolean hidden) {
        if (permanent) return;
        if (this.hidden == hidden) return;
        this.hidden = hidden;
        apply(true);
    }

    private void apply(boolean silent) {
        boolean target = desired && !hidden
                && !com.visualmod.module.modules.util.SafeMode.isLocked();
        if (enabled == target) return;
        enabled = target;
        if (!target) {
            onDisable();
            return;
        }
        if (!silent) {
            onEnable();
            return;
        }
        boolean prev = programmatic;
        programmatic = true;
        try {
            onEnable();
        } finally {
            programmatic = prev;
        }
    }

    protected void makePermanent() {
        this.permanent = true;
        this.hidden = true;
        this.desired = true;
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
