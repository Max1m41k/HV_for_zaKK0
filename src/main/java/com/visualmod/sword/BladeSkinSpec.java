package com.visualmod.sword;

/**
 * Что красить в стрипе клинка: стихия BladeElements, аура SwordAura или обе сразу.
 *
 * Слои живут в одном паке и в одной анимации, поэтому состояние собирается в один ключ —
 * по нему арбитр решает, пора ли перезаписывать текстуры и перезагружать ресурсы.
 */
public record BladeSkinSpec(String element, int elementColor, float elementStrength,
                            String aura, int auraColor, float auraStrength,
                            int frametime, String paint) {

    public boolean hasElement() {
        return element != null;
    }

    public boolean hasAura() {
        return aura != null;
    }

    /** Ходит ли текстура: без движения стрип — это одна картинка, и анимация ей не нужна. */
    public boolean hasMotion() {
        return hasElement() || hasAura();
    }

    public boolean hasPaint() {
        return paint != null;
    }

    public String key() {
        StringBuilder sb = new StringBuilder();
        sb.append(element == null ? "-" : element + "|" + Integer.toHexString(elementColor)
                + "|" + Math.round(elementStrength * 10f));
        sb.append('#');
        sb.append(aura == null ? "-" : aura + "|" + Integer.toHexString(auraColor)
                + "|" + Math.round(auraStrength * 10f));
        return sb.append('#').append(frametime).append('#').append(paint == null ? "-" : paint).toString();
    }
}
